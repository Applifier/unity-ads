//
//  UnityAdsInitializer.h
//  UnityAds
//
//  Created by Pekka Palmu on 4/5/13.
//  Copyright (c) 2013 Unity Technologies. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "../UnityAdsDevice/UnityAdsDevice.h"
#import "../UnityAdsProperties/UnityAdsConstants.h"
#import "../UnityAdsProperties/UnityAdsProperties.h"
#import "../UnityAdsCampaign/UnityAdsCampaignManager.h"
#import "../UnityAdsView/UnityAdsMainViewController.h"
#import "../UnityAdsData/UnityAdsAnalyticsUploader.h"

#import "../UnityAds.h"

@protocol UnityAdsInitializerDelegate <NSObject>

@required
- (void)initComplete;
- (void)initFailed;
@end

@interface UnityAdsInitializer : NSObject <UnityAdsCampaignManagerDelegate>
  @property (nonatomic, weak) id<UnityAdsInitializerDelegate> delegate;
  @property (nonatomic, strong) NSThread *backgroundThread;
  @property (nonatomic, assign) dispatch_queue_t queue;

- (void)initAds:(NSDictionary *)options;
- (BOOL)initWasSuccessfull;
- (void)checkForVersionAndShowAlertDialog;
- (void)reInitialize;
- (void)deInitialize;

- (void)initCampaignManager;
- (void)refreshCampaignManager;
- (void)initAnalyticsUploader;

@end
