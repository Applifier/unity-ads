//
//  UnityAdsConstants.h
//  UnityAds
//
//  Created by bluesun on 1/10/13.
//  Copyright (c) 2013 Unity Technologies. All rights reserved.
//

#import <Foundation/Foundation.h>

/* WebView */

typedef enum {
  kUnityAdsViewStateTypeOfferScreen,
  kUnityAdsViewStateTypeEndScreen,
  kUnityAdsViewStateTypeVideoPlayer,
  kUnityAdsViewStateTypeNone,
  kUnityAdsViewStateTypeInvalid
} UnityAdsViewStateType;

typedef enum {
  kUnityAdsStateActionWillLeaveApplication,
  kUnityAdsStateActionVideoStartedPlaying,
  kUnityAdsStateActionVideoPlaybackEnded,
  kUnityAdsStateActionVideoPlaybackSkipped
} UnityAdsViewStateAction;

extern NSString * const kUnityAdsWebViewJSPrefix;
extern NSString * const kUnityAdsWebViewJSInit;
extern NSString * const kUnityAdsWebViewJSChangeView;
extern NSString * const kUnityAdsWebViewJSHandleNativeEvent;

extern NSString * const kUnityAdsWebViewAPIActionKey;
extern NSString * const kUnityAdsWebViewAPIPlayVideo;
extern NSString * const kUnityAdsWebViewAPINavigateTo;
extern NSString * const kUnityAdsWebViewAPIInitComplete;
extern NSString * const kUnityAdsWebViewAPIClose;
extern NSString * const kUnityAdsWebViewAPIOpen;
extern NSString * const kUnityAdsWebViewAPIDeveloperOptions;
extern NSString * const kUnityAdsWebViewAPIAppStore;
extern NSString * const kUnityAdsWebViewAPIActionVideoStartedPlaying;
extern NSString * const kUnityAdsWebViewAPIActionVideoPlaybackError;

extern NSString * const kUnityAdsWebViewViewTypeCompleted;
extern NSString * const kUnityAdsWebViewViewTypeStart;
extern NSString * const kUnityAdsWebViewViewTypeNone;

extern NSString * const kUnityAdsWebViewDataParamCampaignDataKey;
extern NSString * const kUnityAdsWebViewDataParamPlatformKey;
extern NSString * const kUnityAdsWebViewDataParamDeviceIdKey;
extern NSString * const kUnityAdsWebViewDataParamGameIdKey;
extern NSString * const kUnityAdsWebViewDataParamDeviceTypeKey;
extern NSString * const kUnityAdsWebViewDataParamIdentifierForVendorKey;
extern NSString * const kUnityAdsWebViewDataParamOpenUdidIdKey;
extern NSString * const kUnityAdsWebViewDataParamMacAddressKey;
extern NSString * const kUnityAdsWebViewDataParamSdkVersionKey;
extern NSString * const kUnityAdsWebViewDataParamSdkIsCurrentKey;
extern NSString * const kUnityAdsWebViewDataParamIosVersionKey;
extern NSString * const kUnityAdsWebViewDataParamZoneKey;
extern NSString * const kUnityAdsWebViewDataParamZonesKey;
extern NSString * const kUnityAdsWebViewDataParamUnityVersionKey;

extern NSString * const kUnityAdsWebViewEventDataCampaignIdKey;
extern NSString * const kUnityAdsWebViewEventDataRewatchKey;
extern NSString * const kUnityAdsWebViewEventDataClickUrlKey;
extern NSString * const kUnityAdsWebViewEventDataBypassAppSheetKey;

/* Web Data */

extern int const kUnityAdsWebDataMaxRetryCount;
extern int const kUnityAdsWebDataRetryInterval;

/* Native Events */

extern NSString * const kUnityAdsNativeEventHideSpinner;
extern NSString * const kUnityAdsNativeEventShowSpinner;
extern NSString * const kUnityAdsNativeEventShowError;
extern NSString * const kUnityAdsNativeEventVideoCompleted;
extern NSString * const kUnityAdsNativeEventCampaignIdKey;
extern NSString * const kUnityAdsNativeEventForceStopVideoPlayback;

/* Native Event Params */

extern NSString * const kUnityAdsTextKeyKey;
extern NSString * const kUnityAdsTextKeyBuffering;
extern NSString * const kUnityAdsTextKeyLoading;
extern NSString * const kUnityAdsItemKeyKey;
extern NSString * const kUnityAdsTextKeyVideoPlaybackError;


/* JSON Data Root */

extern NSString * const kUnityAdsJsonDataRootKey;


/* Campaign JSON Properties */

extern NSString * const kUnityAdsCampaignsKey;
extern NSString * const kUnityAdsCampaignEndScreenKey;
extern NSString * const kUnityAdsCampaignEndScreenPortraitKey;
extern NSString * const kUnityAdsCampaignClickURLKey;
extern NSString * const kUnityAdsCampaignCustomClickURLKey;
extern NSString * const kUnityAdsCampaignPictureKey;
extern NSString * const kUnityAdsCampaignTrailerDownloadableKey;
extern NSString * const kUnityAdsCampaignTrailerStreamingKey;
extern NSString * const kUnityAdsCampaignGameIDKey;
extern NSString * const kUnityAdsCampaignGameNameKey;
extern NSString * const kUnityAdsCampaignIDKey;
extern NSString * const kUnityAdsCampaignTaglineKey;
extern NSString * const kUnityAdsCampaignStoreIDKey;
extern NSString * const kUnityAdsCampaignCacheVideoKey;
extern NSString * const kUnityAdsCampaignAllowedToCacheVideoKey;
extern NSString * const kUnityAdsCampaignBypassAppSheet;
extern NSString * const kUnityAdsCampaignExpectedFileSize;
extern NSString * const kUnityAdsCampaignGameIconKey;
extern NSString * const kUnityAdsCampaignAllowVideoSkipKey;
extern NSString * const kUnityAdsCampaignURLSchemesKey;
extern NSString * const kUnityAdsCampaignAllowStreamingKey;
extern NSString * const kUnityAdsCampaignFilterModeKey;

/* Reward Item JSON Properties */

extern NSString * const kUnityAdsRewardItemKeyKey;
extern NSString * const kUnityAdsRewardNameKey;
extern NSString * const kUnityAdsRewardPictureKey;
extern NSString * const kUnityAdsRewardItemKey;
extern NSString * const kUnityAdsRewardItemsKey;

/* Gamer JSON Properties */

extern NSString * const kUnityAdsGamerIDKey;


/* Unity Ads Base JSON Properties */

extern NSString * const kUnityAdsUrlKey;
extern NSString * const kUnityAdsWebViewUrlKey;
extern NSString * const kUnityAdsAnalyticsUrlKey;
extern NSString * const kUnityAdsSdkVersionKey;
extern NSString * const kUnityAdsAppFilteringKey;
extern NSString * const kUnityAdsUrlSchemeMapKey;
extern NSString * const kUnityAdsInstalledAppsUrlKey;

/* Analytics Uploader */

extern NSString * const kUnityAdsAnalyticsTrackingPath;
extern NSString * const kUnityAdsAnalyticsInstallTrackingPath;
extern NSString * const kUnityAdsAnalyticsQueryDictionaryQueryKey;
extern NSString * const kUnityAdsAnalyticsQueryDictionaryBodyKey;
extern NSString * const kUnityAdsAnalyticsUploaderRequestKey;
extern NSString * const kUnityAdsAnalyticsUploaderConnectionKey;
extern NSString * const kUnityAdsAnalyticsUploaderRetriesKey;
extern NSString * const kUnityAdsAnalyticsSavedUploadsKey;
extern NSString * const kUnityAdsAnalyticsSavedUploadURLKey;
extern NSString * const kUnityAdsAnalyticsSavedUploadBodyKey;
extern NSString * const kUnityAdsAnalyticsSavedUploadHTTPMethodKey;

extern NSString * const kUnityAdsAnalyticsQueryParamGameIdKey;
extern NSString * const kUnityAdsInitQueryParamOdin1IdKey;
extern NSString * const kUnityAdsAnalyticsQueryParamEventTypeKey;
extern NSString * const kUnityAdsAnalyticsQueryParamTrackingIdKey;
extern NSString * const kUnityAdsAnalyticsQueryParamProviderIdKey;
extern NSString * const kUnityAdsAnalyticsQueryParamZoneIdKey;
extern NSString * const kUnityAdsAnalyticsQueryParamCachedPlaybackKey;
extern NSString * const kUnityAdsAnalyticsQueryParamRewardItemKey;
extern NSString * const kUnityAdsAnalyticsQueryParamGamerSIDKey;

extern NSString * const kUnityAdsAnalyticsEventTypeVideoStart;
extern NSString * const kUnityAdsAnalyticsEventTypeVideoFirstQuartile;
extern NSString * const kUnityAdsAnalyticsEventTypeVideoMidPoint;
extern NSString * const kUnityAdsAnalyticsEventTypeVideoThirdQuartile;
extern NSString * const kUnityAdsAnalyticsEventTypeVideoEnd;
extern NSString * const kUnityAdsAnalyticsEventTypeOpenAppStore;

extern NSString * const kUnityAdsTrackingEventTypeVideoStart;
extern NSString * const kUnityAdsTrackingEventTypeVideoEnd;


/* Devicetypes */

extern NSString * const kUnityAdsDeviceIphone;
extern NSString * const kUnityAdsDeviceIpod;
extern NSString * const kUnityAdsDeviceIpad;
extern NSString * const kUnityAdsDeviceIosUnknown;
extern NSString * const kUnityAdsDeviceSimulator;

/* Init Query Params */

extern NSString * const kUnityAdsInitQueryParamDeviceIdKey;
extern NSString * const kUnityAdsInitQueryParamDeviceTypeKey;
extern NSString * const kUnityAdsInitQueryParamPlatformKey;
extern NSString * const kUnityAdsInitQueryParamGameIdKey;
extern NSString * const kUnityAdsInitQueryParamOpenUdidKey;
extern NSString * const kUnityAdsInitQueryParamMacAddressKey;
extern NSString * const kUnityAdsInitQueryParamRawAdvertisingTrackingIdKey;
extern NSString * const kUnityAdsInitQueryParamAdvertisingTrackingIdKey;
extern NSString * const kUnityAdsInitQueryParamIdentifierForVendor;
extern NSString * const kUnityAdsInitQueryParamNetworkTypeKey;
extern NSString * const kUnityAdsInitQueryParamTrackingEnabledKey;
extern NSString * const kUnityAdsInitQueryParamSoftwareVersionKey;
extern NSString * const kUnityAdsInitQueryParamHardwareVersionKey;
extern NSString * const kUnityAdsInitQueryParamSdkVersionKey;
extern NSString * const kUnityAdsInitQueryParamConnectionTypeKey;
extern NSString * const kUnityAdsInitQueryParamTestKey;
extern NSString * const kUnityAdsInitQueryParamEncryptionKey;
extern NSString * const kUnityAdsInitQueryParamAppFilterListKey;
extern NSString * const kUnityAdsInitQueryParamSendInternalDetailsKey;
extern NSString * const kUnityAdsInitQueryParamCachingSpeedKey;
extern NSString * const kUnityAdsInitQueryParamUnityVersionKey;


/* Zones */

extern NSString * const kUnityAdsZonesRootKey;
extern NSString * const kUnityAdsZoneIdKey;
extern NSString * const kUnityAdsZoneNameKey;
extern NSString * const kUnityAdsZoneDefaultKey;
extern NSString * const kUnityAdsZoneIsIncentivizedKey;
extern NSString * const kUnityAdsZoneRewardItemsKey;
extern NSString * const kUnityAdsZoneDefaultRewardItemKey;
extern NSString * const kUnityAdsZoneAllowOverrides;
extern NSString * const kUnityAdsZoneNoOfferScreenKey;
extern NSString * const kUnityAdsZoneOpenAnimatedKey;
extern NSString * const kUnityAdsZoneMuteVideoSoundsKey;
extern NSString * const kUnityAdsZoneUseDeviceOrientationForVideoKey;
extern NSString * const kUnityAdsZoneAllowVideoSkipInSecondsKey;

@interface UnityAdsConstants : NSObject

@end
