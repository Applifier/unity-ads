//
//  UnityAdsInitializer.m
//  UnityAds
//
//  Created by Pekka Palmu on 4/5/13.
//  Copyright (c) 2013 Unity Technologies. All rights reserved.
//

#import "UnityAdsInitializer.h"
#import "../UnityAds.h"

@implementation UnityAdsInitializer

- (void)initAds:(NSDictionary *)options {
  if (self.queue == nil)
    [self createQueue];
  if (self.backgroundThread == nil)
    [self createBackgroundThread];
}

- (void)reInitialize {
}

- (void)deInitialize {
  [[UnityAdsCampaignManager sharedInstance] performSelector:@selector(cancelAllDownloads) onThread:self.backgroundThread withObject:nil waitUntilDone:NO];
}

- (void)createBackgroundThread {
  if (self.queue != nil && self.backgroundThread == nil) {
    dispatch_sync(self.queue, ^{
      UALOG_DEBUG(@"Starting background thread");
      self.backgroundThread = [[NSThread alloc] initWithTarget:self selector:@selector(_backgroundRunLoop:) object:nil];
      [self.backgroundThread start];
    });
  }
}

- (void)createQueue {
  if (self.queue == nil) {
    self.queue = dispatch_queue_create("com.unity3d.ads.initializer", NULL);
  }
}

- (void)_backgroundRunLoop:(id)dummy {
	@autoreleasepool {
		NSPort *port = [[NSPort alloc] init];
		[port scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
		
		while([[NSThread currentThread] isCancelled] == NO) {
			@autoreleasepool {
				[[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:3.0]];
			}
		}
	}
}

- (void)dealloc {
  dispatch_release(self.queue);
}

- (BOOL)initWasSuccessfull {
  return NO;
}

- (void)checkForVersionAndShowAlertDialog {
  UALOG_DEBUG(@"");
  
  if (![[UnityAdsProperties sharedInstance] sdkIsCurrent]) {
    UALOG_DEBUG(@"Got different sdkVersions, checking further.");
    
    if (![UnityAdsDevice isEncrypted]) {
      if ([UnityAdsDevice isJailbroken]) {
        UALOG_DEBUG(@"Build is not encrypted, but device seems to be jailbroken. Not showing version alert");
        return;
      }
      else {
        // Build is not encrypted and device is not jailbroken, alert dialog is shown that SDK is not the latest version.
        UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Unity Ads SDK"
                                                        message:@"The Unity Ads SDK you are running is not the current version, please update your SDK"
                                                       delegate:nil
                                              cancelButtonTitle:@"OK"
                                              otherButtonTitles:nil];
        [alert show];
      }
    }
  }
}

- (void)waitForReachabilityCheck {
  [UnityAdsDevice launchReachabilityCheck];
}

- (void)initCampaignManager {
	UAAssert(![NSThread isMainThread]);
	UALOG_DEBUG(@"");
  [(UnityAdsCampaignManager *)[UnityAdsCampaignManager sharedInstance] setDelegate:self];
	[self refreshCampaignManager];
}

- (void)refreshCampaignManager {
	UAAssert(![NSThread isMainThread]);
  [self waitForReachabilityCheck];
	[[UnityAdsProperties sharedInstance] refreshCampaignQueryString];
	[[UnityAdsCampaignManager sharedInstance] updateCampaigns];
}

- (void)initAnalyticsUploader {
	UAAssert(![NSThread isMainThread]);
	UALOG_DEBUG(@"");
	[[UnityAdsAnalyticsUploader sharedInstance] retryFailedUploads];
}

#pragma mark - UnityAdsCampaignManagerDelegate

- (void)campaignManager:(UnityAdsCampaignManager *)campaignManager updatedWithCampaigns:(NSArray *)campaigns gamerID:(NSString *)gamerID {
}

- (void)campaignManagerCampaignDataReceived {
}

- (void)campaignManagerCampaignDataFailed {
}

@end
