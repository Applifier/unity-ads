//
//  UnityAdsiOS4.m
//  UnityAdsExample
//
//  Created by Johan Halin on 9/4/12.
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#include <sys/socket.h>
#include <sys/sysctl.h>
#include <net/if.h>
#include <net/if_dl.h>
#include <CommonCrypto/CommonDigest.h>

#import <AVFoundation/AVFoundation.h>
#import "UnityAdsiOS4.h"
#import "UnityAdsCampaignManager.h"
#import "UnityAdsCampaign.h"
#import "UnityAdsRewardItem.h"
#import "UnityAdsOpenUDID.h"
#import "UnityAdsAnalyticsUploader.h"

// FIXME: this is (obviously) NOT the final URL!
NSString * const kUnityAdsTestWebViewURL = @"http://ads-proto.local/index.html";

typedef enum
{
	kVideoAnalyticsPositionStart = 0,
	kVideoAnalyticsPositionFirstQuartile,
	kVideoAnalyticsPositionMidPoint,
	kVideoAnalyticsPositionThirdQuartile,
	kVideoAnalyticsPositionEnd,
} VideoAnalyticsPosition;

@interface UnityAdsiOS4 () <UnityAdsCampaignManagerDelegate, UIWebViewDelegate>
@property (nonatomic, strong) NSString *gameId;
@property (nonatomic, strong) NSThread *cacheThread;
@property (nonatomic, strong) NSThread *analyticsThread;
@property (nonatomic, strong) UnityAdsCampaignManager *campaignManager;
@property (nonatomic, strong) UIWindow *adsWindow;
@property (nonatomic, strong) UIWebView *webView;
@property (nonatomic, strong) NSArray *campaigns;
@property (nonatomic, strong) UnityAdsRewardItem *rewardItem;
@property (nonatomic, strong) UIView *adView;
@property (nonatomic, strong) UnityAdsCampaign *selectedCampaign;
@property (nonatomic, strong) AVPlayer *player;
@property (nonatomic, strong) AVPlayerLayer *playerLayer;
@property (nonatomic, strong) id timeObserver;
@property (nonatomic, strong) id analyticsTimeObserver;
@property (nonatomic, strong) UILabel *progressLabel;
@property (nonatomic, assign) VideoAnalyticsPosition videoPosition;
@property (nonatomic, strong) UnityAdsAnalyticsUploader *analyticsUploader;
@end

@implementation UnityAdsiOS4

@synthesize gameId = _gameId;
@synthesize cacheThread = _cacheThread;
@synthesize analyticsThread = _analyticsThread;
@synthesize campaignManager = _campaignManager;
@synthesize adsWindow = _adsWindow;
@synthesize webView = _webView;
@synthesize campaigns = _campaigns;
@synthesize rewardItem = _rewardItem;
@synthesize adView = _adView;
@synthesize selectedCampaign = _selectedCampaign;
@synthesize player = _player;
@synthesize playerLayer = _playerLayer;
@synthesize timeObserver = _timeObserver;
@synthesize analyticsTimeObserver = _analyticsTimeObserver;
@synthesize progressLabel = _progressLabel;
@synthesize videoPosition = _videoPosition;
@synthesize analyticsUploader = _analyticsUploader;

#pragma mark - Private

- (NSString *)_macAddress
{
	NSString *interface = @"en0";
	int mgmtInfoBase[6];
	char *msgBuffer = NULL;
	
	// Setup the management Information Base (mib)
	mgmtInfoBase[0] = CTL_NET; // Request network subsystem
	mgmtInfoBase[1] = AF_ROUTE; // Routing table info
	mgmtInfoBase[2] = 0;
	mgmtInfoBase[3] = AF_LINK; // Request link layer information
	mgmtInfoBase[4] = NET_RT_IFLIST; // Request all configured interfaces
	
	// With all configured interfaces requested, get handle index
	if ((mgmtInfoBase[5] = if_nametoindex([interface UTF8String])) == 0)
	{
		NSLog(@"Couldn't get MAC address for interface '%@', if_nametoindex failed.", interface);
		return nil;
	}
	
	size_t length;
	
	// Get the size of the data available (store in len)
	if (sysctl(mgmtInfoBase, 6, NULL, &length, NULL, 0) < 0)
	{
		NSLog(@"Couldn't get MAC address for interface '%@', sysctl for mgmtInfoBase length failed.", interface);
		return nil;
	}
	
	// Alloc memory based on above call
	if ((msgBuffer = malloc(length)) == NULL)
	{
		NSLog(@"Couldn't get MAC address for interface '%@', malloc for %zd bytes failed.", interface, length);
		return nil;
	}
	
	// Get system information, store in buffer
	if (sysctl(mgmtInfoBase, 6, msgBuffer, &length, NULL, 0) < 0)
	{
		free(msgBuffer);
		
		NSLog(@"Couldn't get MAC address for interface '%@', sysctl for mgmtInfoBase data failed.", interface);
		return nil;
	}
	
	// Map msgbuffer to interface message structure
	struct if_msghdr *interfaceMsgStruct = (struct if_msghdr *) msgBuffer;
	
	// Map to link-level socket structure
	struct sockaddr_dl *socketStruct = (struct sockaddr_dl *) (interfaceMsgStruct + 1);
	
	// Copy link layer address data in socket structure to an array
	unsigned char macAddress[6];
	memcpy(&macAddress, socketStruct->sdl_data + socketStruct->sdl_nlen, 6);
	
	// Read from char array into a string object, into MAC address format
	NSString *macAddressString = [NSString stringWithFormat:@"%02X:%02X:%02X:%02X:%02X:%02X", macAddress[0], macAddress[1], macAddress[2], macAddress[3], macAddress[4], macAddress[5]];
	
	// Release the buffer memory
	free(msgBuffer);
	
	return macAddressString;
}

- (NSString *)_md5StringFromString:(NSString *)string
{
	const char *ptr = [string UTF8String];
	unsigned char md5Buffer[CC_MD5_DIGEST_LENGTH];
	CC_MD5(ptr, strlen(ptr), md5Buffer);
	NSMutableString *output = [NSMutableString stringWithCapacity:CC_MD5_DIGEST_LENGTH * 2];
	for(int i = 0; i < CC_MD5_DIGEST_LENGTH; i++)
		[output appendFormat:@"%02x",md5Buffer[i]];
	
	return output;
}

- (NSString *)_md5OpenUDIDString
{
	return [self _md5StringFromString:[UnityAdsOpenUDID value]];
}

- (NSString *)_md5MACAddressString
{
	return [self _md5StringFromString:[self _macAddress]];
}

- (void)_backgroundRunLoop:(id)dummy
{
	@autoreleasepool
	{
		NSPort *port = [[NSPort alloc] init];
		[port scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
		
		while([[NSThread currentThread] isCancelled] == NO)
		{
			@autoreleasepool
			{
				[[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:3.0]];
			}
		}
	}
}

- (void)_startCampaignManager
{
	self.campaignManager = [[UnityAdsCampaignManager alloc] init];
	self.campaignManager.delegate = self;
	[self.campaignManager updateCampaigns];
}

- (void)_selectCampaign:(UnityAdsCampaign *)campaign
{
	if (campaign == nil)
		return;
	
	self.selectedCampaign = campaign;
}

- (void)_configureWebView
{
	self.adsWindow = [[UIWindow alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
	self.webView = [[UIWebView alloc] initWithFrame:self.adsWindow.bounds];
	self.webView.delegate = self;
	self.webView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
	[self.webView loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:kUnityAdsTestWebViewURL]]];
	[self.adsWindow addSubview:self.webView];
}

- (UIView *)_adView
{
	if (self.adView == nil)
	{
		self.adView = [[UIView alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
		self.webView.bounds = self.adView.bounds;
		[self.adView addSubview:self.webView];

		self.progressLabel = [[UILabel alloc] initWithFrame:CGRectZero];
		self.progressLabel.backgroundColor = [UIColor clearColor];
		self.progressLabel.textColor = [UIColor whiteColor];
		self.progressLabel.font = [UIFont systemFontOfSize:12.0];
		self.progressLabel.textAlignment = UITextAlignmentRight;
		self.progressLabel.shadowColor = [UIColor blackColor];
		self.progressLabel.shadowOffset = CGSizeMake(0, 1.0);
		self.progressLabel.autoresizingMask = UIViewAutoresizingFlexibleBottomMargin | UIViewAutoresizingFlexibleWidth;
		[self.adView addSubview:self.progressLabel];
	}
	
	return self.adView;
}

- (Float64)_currentVideoDuration
{
	CMTime durationTime = self.player.currentItem.asset.duration;
	Float64 duration = CMTimeGetSeconds(durationTime);
	
	return duration;
}

- (void)_updateTimeRemainingLabelWithTime:(CMTime)currentTime
{
	Float64 duration = [self _currentVideoDuration];
	Float64 current = CMTimeGetSeconds(currentTime);
	NSString *descriptionText = [NSString stringWithFormat:NSLocalizedString(@"This video ends in %.0f seconds.", nil), duration - current];
	self.progressLabel.text = descriptionText;
}

- (void)_displayProgressLabel
{
	CGFloat padding = 10.0;
	CGFloat height = 30.0;
	CGRect labelFrame = CGRectMake(padding, self.adView.frame.size.height - height, self.adView.frame.size.width - (padding * 2.0), height);
	self.progressLabel.frame = labelFrame;
	self.progressLabel.hidden = NO;
	[self.adView bringSubviewToFront:self.progressLabel];
}

- (NSValue *)_valueWithDuration:(Float64)duration
{
	CMTime time = CMTimeMakeWithSeconds(duration, NSEC_PER_SEC);
	return [NSValue valueWithCMTime:time];
}

- (void)_logPositionString:(NSString *)string
{
	[self.analyticsUploader sendViewReportForCampaign:self.selectedCampaign positionString:string];
}

- (void)_logVideoAnalytics
{
	self.videoPosition++;
	NSString *positionString = nil;
	if (self.videoPosition == kVideoAnalyticsPositionStart)
		positionString = @"start";
	else if (self.videoPosition == kVideoAnalyticsPositionFirstQuartile)
		positionString = @"first_quartile";
	else if (self.videoPosition == kVideoAnalyticsPositionMidPoint)
		positionString = @"mid_point";
	else if (self.videoPosition == kVideoAnalyticsPositionThirdQuartile)
		positionString = @"third_quartile";
	else if (self.videoPosition == kVideoAnalyticsPositionEnd)
		positionString = @"end";
	
	[self performSelector:@selector(_logPositionString:) onThread:self.analyticsThread withObject:positionString waitUntilDone:NO];
}

- (void)_playVideo
{
	NSURL *videoURL = [self.campaignManager videoURLForCampaign:self.selectedCampaign];
	if (videoURL == nil)
	{
		NSLog(@"Video not found!");
		return;
	}
	
	AVPlayerItem *item = [AVPlayerItem playerItemWithURL:videoURL];
	self.player = [AVPlayer playerWithPlayerItem:item];
	self.playerLayer = [AVPlayerLayer playerLayerWithPlayer:self.player];
	self.playerLayer.videoGravity = AVLayerVideoGravityResizeAspectFill;
	self.playerLayer.frame = self.adView.bounds;
	[self.adView.layer addSublayer:self.playerLayer];
	
	[self _displayProgressLabel];
	
	__block UnityAdsiOS4 *blockSelf = self;
	self.timeObserver = [self.player addPeriodicTimeObserverForInterval:CMTimeMakeWithSeconds(1, NSEC_PER_SEC) queue:nil usingBlock:^(CMTime time) {
		[blockSelf _updateTimeRemainingLabelWithTime:time];
	}];
	
	self.videoPosition = -1;
	Float64 duration = [self _currentVideoDuration];
	NSMutableArray *analyticsTimeValues = [NSMutableArray array];
	[analyticsTimeValues addObject:[self _valueWithDuration:duration * .25]];
	[analyticsTimeValues addObject:[self _valueWithDuration:duration * .5]];
	[analyticsTimeValues addObject:[self _valueWithDuration:duration * .75]];
	self.analyticsTimeObserver = [self.player addBoundaryTimeObserverForTimes:analyticsTimeValues queue:nil usingBlock:^{
		[blockSelf _logVideoAnalytics];
	}];
	
	[self.player play];
	
	[[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(_videoPlaybackEnded:) name:AVPlayerItemDidPlayToEndTimeNotification object:item];
	
	if ([self.delegate respondsToSelector:@selector(unityAdsVideoStarted:)])
		[self.delegate unityAdsVideoStarted:self];

	[self _logVideoAnalytics];
}

- (void)_videoPlaybackEnded:(NSNotification *)notification
{
	if ([self.delegate respondsToSelector:@selector(unityAdsVideoCompleted:)])
		[self.delegate unityAdsVideoCompleted:self];

	[self _logVideoAnalytics];
	
	[self.player removeTimeObserver:self.timeObserver];
	self.timeObserver = nil;
	[self.player removeTimeObserver:self.analyticsTimeObserver];
	self.analyticsTimeObserver = nil;
	
	self.progressLabel.hidden = YES;
	
	[self.playerLayer removeFromSuperlayer];
	// FIXME: use the actual API
	[self.webView stringByEvaluatingJavaScriptFromString:@"document.getElementById('videoStart').style.display = 'none';"];
	[self.webView stringByEvaluatingJavaScriptFromString:@"document.getElementById('videoCompleted').style.display = 'block';"];
}

- (void)_closeAdView
{
	if ([self.delegate respondsToSelector:@selector(unityAdsWillHide:)])
		[self.delegate unityAdsWillHide:self];

	[self.adsWindow addSubview:self.webView];
	[self.adView removeFromSuperview];
}

- (void)_startAnalyticsUploader
{
	self.analyticsUploader = [[UnityAdsAnalyticsUploader alloc] init];
	[self.analyticsUploader retryFailedUploads];
}

#pragma mark - Public

- (void)startWithGameId:(NSString *)gameId
{
	if (self.campaignManager != nil)
		return;
	
	self.gameId = gameId;
	self.cacheThread = [[NSThread alloc] initWithTarget:self selector:@selector(_backgroundRunLoop:) object:nil];
	[self.cacheThread start];
	self.analyticsThread = [[NSThread alloc] initWithTarget:self selector:@selector(_backgroundRunLoop:) object:nil];
	[self.analyticsThread start];
	
	[self performSelector:@selector(_startCampaignManager) onThread:self.cacheThread withObject:nil waitUntilDone:NO];
	[self performSelector:@selector(_startAnalyticsUploader) onThread:self.analyticsThread withObject:nil waitUntilDone:NO];
	
	[self _configureWebView];
}

- (BOOL)show
{
	// FIXME: probably not the best way to accomplish this
	
	if ([self.campaigns count] > 0)
	{
		// merge the following two delegate methods?
		if ([self.delegate respondsToSelector:@selector(unityAdsWillShow:)])
			[self.delegate unityAdsWillShow:self];
		
		if ([self.delegate respondsToSelector:@selector(unityAds:wantsToShowAdView:)])
			[self.delegate unityAds:self wantsToShowAdView:[self _adView]];
		
		return YES;
	}
	
	return NO;
}

- (BOOL)hasCampaigns
{
	return ([self.campaigns count] > 0);
}

- (void)stopAll
{
	[self.campaignManager performSelector:@selector(cancelAllDownloads) onThread:self.cacheThread withObject:nil waitUntilDone:NO];
}

- (void)dealloc
{
	self.campaignManager.delegate = nil;
}

#pragma mark - UnityAdsCampaignManagerDelegate

- (void)campaignManager:(UnityAdsCampaignManager *)campaignManager updatedWithCampaigns:(NSArray *)campaigns rewardItem:(UnityAdsRewardItem *)rewardItem
{
	if ( ! [NSThread isMainThread])
	{
		NSLog(@"Method must be run on main thread.");
		return;
	}
	
	self.campaigns = campaigns;
	self.rewardItem = rewardItem;
	
	if ([self.delegate respondsToSelector:@selector(unityAdsFetchCompleted:)])
		[self.delegate unityAdsFetchCompleted:self];
}

#pragma mark - UIWebViewDelegate

- (BOOL)webView:(UIWebView *)webView shouldStartLoadWithRequest:(NSURLRequest *)request navigationType:(UIWebViewNavigationType)navigationType
{
	// FIXME: this is test code
	NSString *urlString = [[request URL] absoluteString];
	if ([[urlString substringFromIndex:[urlString length] - 1] isEqualToString:@"#"])
	{
		[self _playVideo];
		return NO;
	}
	
	return YES;
}

- (void)webViewDidStartLoad:(UIWebView *)webView
{
}

- (void)webViewDidFinishLoad:(UIWebView *)webView
{
}

- (void)webView:(UIWebView *)webView didFailLoadWithError:(NSError *)error
{
}

@end
