//
//  UnityAdsConstants.m
//  UnityAds
//
//  Created by bluesun on 1/10/13.
//  Copyright (c) 2013 Unity Technologies. All rights reserved.
//

#import "UnityAdsConstants.h"


/* WebView */

NSString * const kUnityAdsWebViewJSPrefix = @"unityads.";
NSString * const kUnityAdsWebViewJSInit = @"init";
NSString * const kUnityAdsWebViewJSChangeView = @"setView";
NSString * const kUnityAdsWebViewJSHandleNativeEvent = @"handleNativeEvent";

NSString * const kUnityAdsWebViewAPIActionKey = @"action";
NSString * const kUnityAdsWebViewAPIPlayVideo = @"playVideo";
NSString * const kUnityAdsWebViewAPINavigateTo = @"navigateTo";
NSString * const kUnityAdsWebViewAPIInitComplete = @"initComplete";
NSString * const kUnityAdsWebViewAPIClose = @"close";
NSString * const kUnityAdsWebViewAPIOpen = @"open";
NSString * const kUnityAdsWebViewAPIDeveloperOptions = @"developerOptions";
NSString * const kUnityAdsWebViewAPIAppStore = @"appStore";
NSString * const kUnityAdsWebViewAPIActionVideoStartedPlaying = @"video_started_playing";
NSString * const kUnityAdsWebViewAPIActionVideoPlaybackError = @"video_playback_error";

NSString * const kUnityAdsWebViewViewTypeCompleted = @"completed";
NSString * const kUnityAdsWebViewViewTypeStart = @"start";
NSString * const kUnityAdsWebViewViewTypeNone = @"none";

NSString * const kUnityAdsWebViewDataParamCampaignDataKey = @"campaignData";
NSString * const kUnityAdsWebViewDataParamPlatformKey = @"platform";
NSString * const kUnityAdsWebViewDataParamDeviceIdKey = @"deviceId";
NSString * const kUnityAdsWebViewDataParamGameIdKey = @"gameId";
NSString * const kUnityAdsWebViewDataParamDeviceTypeKey = @"deviceType";
NSString * const kUnityAdsWebViewDataParamIdentifierForVendorKey = @"identifierForVendor";
NSString * const kUnityAdsWebViewDataParamOpenUdidIdKey = @"openUdid";
NSString * const kUnityAdsWebViewDataParamMacAddressKey = @"macAddress";
NSString * const kUnityAdsWebViewDataParamSdkVersionKey = @"sdkVersion";
NSString * const kUnityAdsWebViewDataParamIosVersionKey = @"iOSVersion";
NSString * const kUnityAdsWebViewDataParamSdkIsCurrentKey = @"sdkIsCurrent";
NSString * const kUnityAdsWebViewDataParamZoneKey = @"zone";
NSString * const kUnityAdsWebViewDataParamZonesKey = @"zones";
NSString * const kUnityAdsWebViewDataParamUnityVersionKey = @"unityVersion";

NSString * const kUnityAdsWebViewEventDataCampaignIdKey = @"campaignId";
NSString * const kUnityAdsWebViewEventDataRewatchKey = @"rewatch";
NSString * const kUnityAdsWebViewEventDataClickUrlKey = @"clickUrl";
NSString * const kUnityAdsWebViewEventDataBypassAppSheetKey = @"bypassAppSheet";

/* Web Data */

int const kUnityAdsWebDataMaxRetryCount = 5;
int const kUnityAdsWebDataRetryInterval = 5;

/* Native Events */

NSString * const kUnityAdsNativeEventHideSpinner = @"hideSpinner";
NSString * const kUnityAdsNativeEventShowSpinner = @"showSpinner";
NSString * const kUnityAdsNativeEventShowError = @"showError";
NSString * const kUnityAdsNativeEventVideoCompleted = @"videoCompleted";
NSString * const kUnityAdsNativeEventCampaignIdKey = @"campaignId";
NSString * const kUnityAdsNativeEventForceStopVideoPlayback = @"forceStopVideoPlayback";

/* Native Event Params */

NSString * const kUnityAdsTextKeyKey = @"textKey";
NSString * const kUnityAdsTextKeyBuffering = @"buffering";
NSString * const kUnityAdsTextKeyLoading = @"loading";
NSString * const kUnityAdsTextKeyVideoPlaybackError = @"videoPlaybackError";
NSString * const kUnityAdsItemKeyKey = @"itemKey";


/* JSON Data Root */

NSString * const kUnityAdsJsonDataRootKey = @"data";


/* Campaign JSON Properties */

NSString * const kUnityAdsCampaignsKey = @"campaigns";
NSString * const kUnityAdsCampaignEndScreenKey = @"endScreen";
NSString * const kUnityAdsCampaignEndScreenPortraitKey = @"endScreenPortrait";
NSString * const kUnityAdsCampaignClickURLKey = @"clickUrl";
NSString * const kUnityAdsCampaignCustomClickURLKey = @"customClickUrl";
NSString * const kUnityAdsCampaignPictureKey = @"picture";
NSString * const kUnityAdsCampaignTrailerDownloadableKey = @"trailerDownloadable";
NSString * const kUnityAdsCampaignTrailerStreamingKey = @"trailerStreaming";
NSString * const kUnityAdsCampaignGameIconKey = @"gameIcon";
NSString * const kUnityAdsCampaignGameIDKey = @"gameId";
NSString * const kUnityAdsCampaignGameNameKey = @"gameName";
NSString * const kUnityAdsCampaignIDKey = @"id";
NSString * const kUnityAdsCampaignTaglineKey = @"tagLine";
NSString * const kUnityAdsCampaignStoreIDKey = @"iTunesId";
NSString * const kUnityAdsCampaignCacheVideoKey = @"cacheVideo";
NSString * const kUnityAdsCampaignAllowedToCacheVideoKey = @"allowCache";
NSString * const kUnityAdsCampaignBypassAppSheet = @"bypassAppSheet";
NSString * const kUnityAdsCampaignExpectedFileSize = @"trailerSize";
NSString * const kUnityAdsCampaignAllowVideoSkipKey = @"allowSkipVideoInSeconds";
NSString * const kUnityAdsCampaignURLSchemesKey = @"urlSchemes";
NSString * const kUnityAdsCampaignAllowStreamingKey = @"allowStreaming";
NSString * const kUnityAdsCampaignFilterModeKey = @"filterMode";

/* Reward Item JSON Properties */

NSString * const kUnityAdsRewardItemKeyKey = @"key";
NSString * const kUnityAdsRewardNameKey = @"name";
NSString * const kUnityAdsRewardPictureKey = @"picture";
NSString * const kUnityAdsRewardItemKey = @"item";
NSString * const kUnityAdsRewardItemsKey = @"items";


/* Gamer JSON Properties */

NSString * const kUnityAdsGamerIDKey = @"gamerId";


/* Unity Ads Base JSON Properties */

NSString * const kUnityAdsUrlKey = @"impactUrl";
NSString * const kUnityAdsWebViewUrlKey = @"webViewUrl";
NSString * const kUnityAdsAnalyticsUrlKey = @"analyticsUrl";
NSString * const kUnityAdsSdkVersionKey = @"nativeSdkVersion";
NSString * const kUnityAdsAppFilteringKey = @"appFiltering";
NSString * const kUnityAdsUrlSchemeMapKey = @"urlSchemeMap";
NSString * const kUnityAdsInstalledAppsUrlKey = @"installedAppsUrl";


/* Analytics Uploader */

NSString * const kUnityAdsAnalyticsTrackingPath = @"gamers/";
NSString * const kUnityAdsAnalyticsInstallTrackingPath = @"games/";
NSString * const kUnityAdsAnalyticsQueryDictionaryQueryKey = @"kUnityAdsQueryDictionaryQueryKey";
NSString * const kUnityAdsAnalyticsQueryDictionaryBodyKey = @"kUnityAdsQueryDictionaryBodyKey";
NSString * const kUnityAdsAnalyticsUploaderRequestKey = @"kUnityAdsAnalyticsUploaderRequestKey";
NSString * const kUnityAdsAnalyticsUploaderConnectionKey = @"kUnityAdsAnalyticsUploaderConnectionKey";
NSString * const kUnityAdsAnalyticsUploaderRetriesKey = @"kUnityAdsAnalyticsUploaderRetriesKey";
NSString * const kUnityAdsAnalyticsSavedUploadsKey = @"kUnityAdsAnalyticsSavedUploadsKey";
NSString * const kUnityAdsAnalyticsSavedUploadURLKey = @"kUnityAdsAnalyticsSavedUploadURLKey";
NSString * const kUnityAdsAnalyticsSavedUploadBodyKey = @"kUnityAdsAnalyticsSavedUploadBodyKey";
NSString * const kUnityAdsAnalyticsSavedUploadHTTPMethodKey = @"kUnityAdsAnalyticsSavedUploadHTTPMethodKey";

NSString * const kUnityAdsAnalyticsQueryParamGameIdKey = @"gameId";
NSString * const kUnityAdsAnalyticsQueryParamEventTypeKey = @"type";
NSString * const kUnityAdsAnalyticsQueryParamTrackingIdKey = @"trackingId";
NSString * const kUnityAdsAnalyticsQueryParamProviderIdKey = @"providerId";
NSString * const kUnityAdsAnalyticsQueryParamZoneIdKey = @"zone";
NSString * const kUnityAdsAnalyticsQueryParamCachedPlaybackKey = @"cachedPlayback";
NSString * const kUnityAdsAnalyticsQueryParamRewardItemKey = @"rewardItem";
NSString * const kUnityAdsAnalyticsQueryParamGamerSIDKey = @"sid";

NSString * const kUnityAdsAnalyticsEventTypeVideoStart = @"video_start";
NSString * const kUnityAdsAnalyticsEventTypeVideoFirstQuartile = @"first_quartile";
NSString * const kUnityAdsAnalyticsEventTypeVideoMidPoint = @"mid_point";
NSString * const kUnityAdsAnalyticsEventTypeVideoThirdQuartile = @"third_quartile";
NSString * const kUnityAdsAnalyticsEventTypeVideoEnd = @"video_end";
NSString * const kUnityAdsAnalyticsEventTypeOpenAppStore = @"openAppStore";

NSString * const kUnityAdsTrackingEventTypeVideoStart = @"start";
NSString * const kUnityAdsTrackingEventTypeVideoEnd = @"view";


/* Devicetypes */

NSString * const kUnityAdsDeviceIphone = @"iphone";
NSString * const kUnityAdsDeviceIpod = @"ipod";
NSString * const kUnityAdsDeviceIpad = @"ipad";
NSString * const kUnityAdsDeviceIosUnknown = @"iosUnknown";
NSString * const kUnityAdsDeviceSimulator = @"simulator";


/* Init Query Params */

NSString * const kUnityAdsInitQueryParamDeviceIdKey = @"deviceId";
NSString * const kUnityAdsInitQueryParamDeviceTypeKey = @"deviceType";
NSString * const kUnityAdsInitQueryParamPlatformKey = @"platform";
NSString * const kUnityAdsInitQueryParamGameIdKey = @"gameId";
NSString * const kUnityAdsInitQueryParamOpenUdidKey = @"openUdid";
NSString * const kUnityAdsInitQueryParamOdin1IdKey = @"odin1Id";
NSString * const kUnityAdsInitQueryParamMacAddressKey = @"macAddress";
NSString * const kUnityAdsInitQueryParamRawAdvertisingTrackingIdKey = @"rawAdvertisingTrackingId";
NSString * const kUnityAdsInitQueryParamAdvertisingTrackingIdKey = @"advertisingTrackingId";
NSString * const kUnityAdsInitQueryParamIdentifierForVendor = @"identifierForVendor";
NSString * const kUnityAdsInitQueryParamNetworkTypeKey = @"iosNetworkType";
NSString * const kUnityAdsInitQueryParamTrackingEnabledKey = @"trackingEnabled";
NSString * const kUnityAdsInitQueryParamSoftwareVersionKey = @"softwareVersion";
NSString * const kUnityAdsInitQueryParamHardwareVersionKey = @"hardwareVersion";
NSString * const kUnityAdsInitQueryParamSdkVersionKey = @"sdkVersion";
NSString * const kUnityAdsInitQueryParamConnectionTypeKey = @"connectionType";
NSString * const kUnityAdsInitQueryParamTestKey = @"test";
NSString * const kUnityAdsInitQueryParamEncryptionKey = @"encrypted";
NSString * const kUnityAdsInitQueryParamSendInternalDetailsKey = @"sendInternalDetails";
NSString * const kUnityAdsInitQueryParamAppFilterListKey = @"appFilterList";
NSString * const kUnityAdsInitQueryParamCachingSpeedKey = @"cachingSpeed";
NSString * const kUnityAdsInitQueryParamUnityVersionKey = @"unityVersion";


/* Zones */

NSString * const kUnityAdsZonesRootKey = @"zones";
NSString * const kUnityAdsZoneIdKey = @"id";
NSString * const kUnityAdsZoneNameKey = @"name";
NSString * const kUnityAdsZoneDefaultKey = @"default";
NSString * const kUnityAdsZoneIsIncentivizedKey = @"incentivised";
NSString * const kUnityAdsZoneRewardItemsKey = @"rewardItems";
NSString * const kUnityAdsZoneDefaultRewardItemKey = @"defaultRewardItem";
NSString * const kUnityAdsZoneAllowOverrides = @"allowClientOverrides";
NSString * const kUnityAdsZoneNoOfferScreenKey = @"noOfferScreen";
NSString * const kUnityAdsZoneOpenAnimatedKey = @"openAnimated";
NSString * const kUnityAdsZoneMuteVideoSoundsKey = @"muteVideoSounds";
NSString * const kUnityAdsZoneUseDeviceOrientationForVideoKey = @"useDeviceOrientationForVideo";
NSString * const kUnityAdsZoneAllowVideoSkipInSecondsKey = @"allowVideoSkipInSeconds";

@implementation UnityAdsConstants

@end
