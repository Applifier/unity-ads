//
//  UnityAdsWebAppController.m
//  UnityAds
//
//  Created by bluesun on 10/23/12.
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import "UnityAdsWebAppController.h"
#import "../UnityAds.h"
#import "../UnityAdsURLProtocol/UnityAdsURLProtocol.h"
#import "../UnityAdsSBJSON/UnityAdsSBJsonWriter.h"
#import "../UnityAdsSBJSON/NSObject+UnityAdsSBJson.h"
#import "../UnityAdsCampaign/UnityAdsCampaign.h"
#import "../UnityAdsCampaign/UnityAdsCampaignManager.h"
#import "../UnityAdsDevice/UnityAdsDevice.h"
#import "../UnityAdsView/UnityAdsMainViewController.h"
#import "../UnityAdsProperties/UnityAdsProperties.h"
#import "../UnityAdsProperties/UnityAdsConstants.h"
#import "../UnityAdsZone/UnityAdsZoneManager.h"

@interface UnityAdsWebAppController ()
  @property (nonatomic, strong) NSDictionary* webAppInitalizationParams;
@end

@implementation UnityAdsWebAppController

- (UnityAdsWebAppController *)init {
  if (self = [super init]) {
  }
  return self;
}

static UnityAdsWebAppController *sharedWebAppController = nil;

+ (instancetype)sharedInstance {
	@synchronized(self) {
		if (sharedWebAppController == nil) {
      sharedWebAppController = [[UnityAdsWebAppController alloc] init];
      [sharedWebAppController setWebViewInitialized:NO];
      [sharedWebAppController setWebViewLoaded:NO];
    }
	}
	
	return sharedWebAppController;
}

- (void)loadWebApp:(NSDictionary *)webAppParams {
	self.webViewLoaded = NO;
	self.webViewInitialized = NO;
  _webAppInitalizationParams = webAppParams;
  [NSURLProtocol registerClass:[UnityAdsURLProtocol class]];
	[self.webView loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:[[UnityAdsProperties sharedInstance] webViewBaseUrl]]]];
}

- (void)setupWebApp:(CGRect)frame {  
  if (self.webView == nil) {
    self.webView = [[UIWebView alloc] initWithFrame:frame];
    self.webView.delegate = self;
    self.webView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.webView.scalesPageToFit = NO;
    [self.webView setBackgroundColor:[UIColor blackColor]];
    UIScrollView *scrollView = nil;
    
    if ([self.webView respondsToSelector:@selector(scrollView)]) {
      scrollView = self.webView.scrollView;
    }
    else {
      UIView *view = [self.webView.subviews lastObject];
      if ([view isKindOfClass:[UIScrollView class]])
        scrollView = (UIScrollView *)view;
    }
    
    if (scrollView != nil) {
      scrollView.delegate = self;
      scrollView.showsVerticalScrollIndicator = NO;
    }
  }
}

- (void)setWebViewCurrentView:(NSString *)view data:(NSDictionary *)data {
	NSString *js = [NSString stringWithFormat:@"%@%@(\"%@\", %@);", kUnityAdsWebViewJSPrefix, kUnityAdsWebViewJSChangeView, view, [data JSONRepresentation]];
  
  UALOG_DEBUG(@"");
  [self runJavascriptDependingOnPlatform:js];
}

- (void)sendNativeEventToWebApp:(NSString *)eventType data:(NSDictionary *)data {
 	NSString *js = [NSString stringWithFormat:@"%@%@(\"%@\", %@);", kUnityAdsWebViewJSPrefix, kUnityAdsWebViewJSHandleNativeEvent, eventType, [data JSONRepresentation]];
  
  UALOG_DEBUG(@"");
  [self runJavascriptDependingOnPlatform:js];
}

- (void)handleWebEvent:(NSString *)type data:(NSDictionary *)data {
  UALOG_DEBUG(@"Gotevent: %@ withData: %@", type, data);
  
  if ([type isEqualToString:kUnityAdsWebViewAPIPlayVideo] || [type isEqualToString:kUnityAdsWebViewAPINavigateTo] || [type isEqualToString:kUnityAdsWebViewAPIAppStore])
	{
		if ([type isEqualToString:kUnityAdsWebViewAPIPlayVideo]) {
      if ([data objectForKey:kUnityAdsWebViewEventDataCampaignIdKey] != nil) {
        if ([[[UnityAdsMainViewController sharedInstance] getCurrentViewState] getStateType] != kUnityAdsViewStateTypeVideoPlayer &&
            ![[UnityAdsMainViewController sharedInstance] isClosing]) {
          [self _selectCampaignWithID:[data objectForKey:kUnityAdsWebViewEventDataCampaignIdKey]];
          [[UnityAdsMainViewController sharedInstance] changeState:kUnityAdsViewStateTypeVideoPlayer withOptions:data];
        }
        else {
           UALOG_DEBUG(@"Cannot start video: %i, %i", [[UnityAdsMainViewController sharedInstance] isClosing], [[[UnityAdsMainViewController sharedInstance] getCurrentViewState] getStateType]);
        }
      }
		}
		else if ([type isEqualToString:kUnityAdsWebViewAPINavigateTo]) {
      if ([data objectForKey:kUnityAdsWebViewEventDataClickUrlKey] != nil) {
        [self openExternalUrl:[data objectForKey:kUnityAdsWebViewEventDataClickUrlKey]];
      }
    
		}
		else if ([type isEqualToString:kUnityAdsWebViewAPIAppStore]) {
      if ([data objectForKey:kUnityAdsWebViewEventDataClickUrlKey] != nil) {
        [[UnityAdsMainViewController sharedInstance] applyOptionsToCurrentState:data];
      }    
		}
	}
	else if ([type isEqualToString:kUnityAdsWebViewAPIClose]) {
    if ([[[UnityAdsMainViewController sharedInstance] getCurrentViewState] getStateType] != kUnityAdsViewStateTypeVideoPlayer &&
        ![[UnityAdsMainViewController sharedInstance] isClosing]) {
      [[UnityAdsMainViewController sharedInstance] closeAds:YES withAnimations:YES withOptions:nil];
    }
    else {
      UALOG_DEBUG(@"Preventing sending close from WebView: %i, %i", [[UnityAdsMainViewController sharedInstance] isClosing], [[[UnityAdsMainViewController sharedInstance] getCurrentViewState] getStateType]);
    }
	}
	else if ([type isEqualToString:kUnityAdsWebViewAPIInitComplete]) {
    self.webViewInitialized = YES;
    
    if (self.delegate != nil) {
      [self.delegate webAppReady];
    }
	}
}

- (void)runJavascriptDependingOnPlatform:(NSString *)javaScriptString {
    dispatch_async(dispatch_get_main_queue(), ^{
      [self runJavascript:javaScriptString];
    });
}

- (void)runJavascript:(NSString *)javaScriptString {
  NSString *returnValue = nil;
  
  if (javaScriptString != nil) {
    UALOG_DEBUG(@"Running JavaScriptString: %@", javaScriptString);
    returnValue = [self.webView stringByEvaluatingJavaScriptFromString:javaScriptString];
  }
  
  if (returnValue != nil) {
    if ([returnValue isEqualToString:@"true"]) {
      UALOG_DEBUG(@"JavaScript call successfull.");
    }
    else {
      UALOG_DEBUG(@"Got unexpected response when running javascript: %@", returnValue);
    }
  }
  else {
    UALOG_DEBUG(@"JavaScript call failed!");
  }
}

- (void)_selectCampaignWithID:(NSString *)campaignId {
	[[UnityAdsCampaignManager sharedInstance] setSelectedCampaign:nil];
	
	if (campaignId == nil) {
		UALOG_DEBUG(@"Input is nil.");
		return;
	}
  
	UnityAdsCampaign *campaign = [[UnityAdsCampaignManager sharedInstance] getCampaignWithId:campaignId];
	
	if (campaign != nil) {
		[[UnityAdsCampaignManager sharedInstance] setSelectedCampaign:campaign];
	}
	else {
    UALOG_DEBUG(@"No campaign with id '%@' found.", campaignId);
  }		
}

- (void)openExternalUrl:(NSString *)urlString {
	if (urlString == nil) {
		UALOG_DEBUG(@"No URL set.");
		return;
	}
	
  dispatch_async(dispatch_get_main_queue(), ^{
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:urlString]];
  });
}


- (void)initWebApp {
  UAAssert([NSThread isMainThread]);
    
  NSMutableDictionary * webAppValues = [[NSMutableDictionary alloc] init];
    
  [webAppValues setValue: [[UnityAdsCampaignManager sharedInstance] campaignData] forKey:kUnityAdsWebViewDataParamCampaignDataKey];
  [webAppValues setValue: @"ios"                                                         forKey:kUnityAdsWebViewDataParamPlatformKey];
  [webAppValues setValue: [UnityAdsDevice md5DeviceId]                            forKey:kUnityAdsWebViewDataParamDeviceIdKey];
  [webAppValues setValue: [UnityAdsDevice md5MACAddressString]                    forKey:kUnityAdsWebViewDataParamMacAddressKey];
  [webAppValues setValue: [[UnityAdsProperties sharedInstance] adsVersion]     forKey:kUnityAdsWebViewDataParamSdkVersionKey];
  [webAppValues setValue: [[UnityAdsProperties sharedInstance] adsGameId]      forKey:kUnityAdsWebViewDataParamGameIdKey];
  [webAppValues setValue: [UnityAdsDevice softwareVersion]                        forKey:kUnityAdsWebViewDataParamIosVersionKey];
  [webAppValues setValue: [UnityAdsDevice analyticsMachineName]                   forKey:kUnityAdsWebViewDataParamDeviceTypeKey];
    
  [self setupWebApp:[[UIScreen mainScreen] bounds]];
  [self loadWebApp:webAppValues];
}

#pragma mark - WebView

- (void)initWebAppWithValues:(NSDictionary *)values {
	NSString *js = [NSString stringWithFormat:@"%@%@(%@);", kUnityAdsWebViewJSPrefix, kUnityAdsWebViewJSInit, [values JSONRepresentation]];
  UALOG_DEBUG(@"");
  [self runJavascript:js];
}


#pragma mark - UIWebViewDelegate

- (BOOL)webView:(UIWebView *)webView shouldStartLoadWithRequest:(NSURLRequest *)request navigationType:(UIWebViewNavigationType)navigationType {
	NSURL *url = [request URL];
	UALOG_DEBUG(@"url %@", url);
	
  if ([[url scheme] isEqualToString:@"itms-apps"]) {
		return NO;
	}
	
	return YES;
}

- (void)webViewDidStartLoad:(UIWebView *)webView {
	UALOG_DEBUG(@"");
}

- (void)webViewDidFinishLoad:(UIWebView *)webView {
	UALOG_DEBUG(@"");
	
	self.webViewLoaded = YES;
	
	if (!self.webViewInitialized)
		[self initWebAppWithValues:_webAppInitalizationParams];
}

- (void)webView:(UIWebView *)webView didFailLoadWithError:(NSError *)error {
	UALOG_DEBUG(@"%@", error);
}


#pragma mark - UIScrollViewDelegate

- (void)scrollViewDidScroll:(UIScrollView *)scrollView {
	scrollView.contentOffset = CGPointMake(scrollView.contentOffset.x, 0);
}

@end