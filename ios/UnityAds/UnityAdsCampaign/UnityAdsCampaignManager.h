//
//  UnityAdsCampaignManager.h
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import <Foundation/Foundation.h>

@class UnityAdsCampaignManager;
@class UnityAdsRewardItem;
@class UnityAdsCampaign;

@protocol UnityAdsCampaignManagerDelegate <NSObject>

@required
- (void)campaignManager:(UnityAdsCampaignManager *)campaignManager updatedWithCampaigns:(NSArray *)campaigns gamerID:(NSString *)gamerID;
- (void)campaignManagerCampaignDataReceived;
- (void)campaignManagerCampaignDataFailed;

@end

@interface UnityAdsCampaignManager : NSObject

@property (nonatomic, weak) id <UnityAdsCampaignManagerDelegate> delegate;
@property (nonatomic, strong) NSArray *campaigns;
@property (nonatomic, strong) NSDictionary *campaignData;
@property (nonatomic, strong) UnityAdsCampaign *selectedCampaign;

- (void)updateCampaigns;
- (NSURL *)getVideoURLForCampaign:(UnityAdsCampaign *)campaign;
- (void)cancelAllDownloads;
- (UnityAdsCampaign *)getCampaignWithId:(NSString *)campaignId;
- (UnityAdsCampaign *)getCampaignWithITunesId:(NSString *)iTunesId;
- (UnityAdsCampaign *)getCampaignWithClickUrl:(NSString *)clickUrl;
- (NSArray *)getViewableCampaigns;
- (void)cacheNextCampaignAfter:(UnityAdsCampaign *)currentCampaign;

+ (instancetype)sharedInstance;

@end
