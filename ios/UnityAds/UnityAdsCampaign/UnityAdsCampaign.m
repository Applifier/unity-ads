//
//  UnityAdsCampaign.m
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import "../UnityAds.h"
#import "../UnityAdsProperties/UnityAdsConstants.h"

#import "UnityAdsCampaign.h"

@interface UnityAdsCampaign ()
@end

@implementation UnityAdsCampaign

- (id)initWithData:(NSDictionary *)data {
  self = [super init];
  if (self) {
    self.isValidCampaign = false;
    [self setupFromData:data];
  }
  return self;
}

- (long long)geBufferingDuration {
  if (self.videoBufferingEndTime == 0) {
    self.videoBufferingEndTime = [[NSDate date] timeIntervalSince1970] * 1000;
  }
  
  if (self.videoBufferingStartTime > 0) {
    return self.videoBufferingEndTime - self.videoBufferingStartTime;
  }
  
  return 0;
}

- (void)setupFromData:(NSDictionary *)data {
  BOOL failedData = false;
  
  self.viewed = NO;
  self.nativeTrackingQuerySent = false;
  self.videoBufferingEndTime = 0;
  self.videoBufferingStartTime  = 0;
  
  NSString *endScreenURLString = [data objectForKey:kUnityAdsCampaignEndScreenKey];
  if (endScreenURLString == nil) failedData = true;
  NSURL *endScreenURL = [NSURL URLWithString:endScreenURLString];
  self.endScreenURL = endScreenURL;
  
  NSString *endScreenPortraitURLString = [data objectForKey:kUnityAdsCampaignEndScreenPortraitKey];
  if (endScreenPortraitURLString != nil) {
    NSURL *endScreenPortraitURL = [NSURL URLWithString:endScreenPortraitURLString];
    UALOG_DEBUG(@"Found endScreenPortraitURL");
    self.endScreenPortraitURL = endScreenPortraitURL;
  }
    
  NSString *clickURLString = [data objectForKey:kUnityAdsCampaignClickURLKey];
  if (clickURLString == nil) failedData = true;
  NSURL *clickURL = [NSURL URLWithString:clickURLString];
  self.clickURL = clickURL;
  
  NSString *pictureURLString = [data objectForKey:kUnityAdsCampaignPictureKey];
  if (pictureURLString == nil) failedData = true;
  NSURL *pictureURL = [NSURL URLWithString:pictureURLString];
  self.pictureURL = pictureURL;
  
  NSString *trailerDownloadableURLString = [data objectForKey:kUnityAdsCampaignTrailerDownloadableKey];
  if (trailerDownloadableURLString == nil) failedData = true;
  NSURL *trailerDownloadableURL = [NSURL URLWithString:trailerDownloadableURLString];
  self.trailerDownloadableURL = trailerDownloadableURL;
  
  NSString *trailerStreamingURLString = [data objectForKey:kUnityAdsCampaignTrailerStreamingKey];
  if (trailerStreamingURLString == nil) failedData = true;
  NSURL *trailerStreamingURL = [NSURL URLWithString:trailerStreamingURLString];
  self.trailerStreamingURL = trailerStreamingURL;
  
  NSString *gameIconURLString = [data objectForKey:kUnityAdsCampaignGameIconKey];
  if (gameIconURLString == nil) failedData = true;
  NSURL *gameIconURL = [NSURL URLWithString:gameIconURLString];
  self.gameIconURL = gameIconURL;
  
  id gameIDValue = [data objectForKey:kUnityAdsCampaignGameIDKey];
  if (gameIDValue == nil) failedData = true;
  NSString *gameID = [gameIDValue isKindOfClass:[NSNumber class]] ? [gameIDValue stringValue] : gameIDValue;
  self.gameID = gameID;
  
  id gameNameValue = [data objectForKey:kUnityAdsCampaignGameNameKey];
  if (gameNameValue == nil) failedData = true;
  NSString *gameName = [gameNameValue isKindOfClass:[NSNumber class]] ? [gameNameValue stringValue] : gameNameValue;
  self.gameName = gameName;
  
  id idValue = [data objectForKey:kUnityAdsCampaignIDKey];
  if (idValue == nil) failedData = true;
  NSString *idString = [idValue isKindOfClass:[NSNumber class]] ? [idValue stringValue] : idValue;
  self.id = idString;
  
  id tagLineValue = [data objectForKey:kUnityAdsCampaignTaglineKey];
  if (tagLineValue == nil) failedData = true;
  NSString *tagline = [tagLineValue isKindOfClass:[NSNumber class]] ? [tagLineValue stringValue] : tagLineValue;
  self.tagLine = tagline;
  
  id itunesIDValue = [data objectForKey:kUnityAdsCampaignStoreIDKey];
  if (itunesIDValue == nil) failedData = true;
  NSString *itunesID = [itunesIDValue isKindOfClass:[NSNumber class]] ? [itunesIDValue stringValue] : itunesIDValue;
  self.itunesID = itunesID;

  self.allowedToCacheVideo = NO;
  if ([data objectForKey:kUnityAdsCampaignAllowedToCacheVideoKey] != nil) {
    if ([[data valueForKey:kUnityAdsCampaignAllowedToCacheVideoKey] boolValue] != 0) {
      self.allowedToCacheVideo = YES;
    }
  }
  
  self.shouldCacheVideo = NO;
  if ([data objectForKey:kUnityAdsCampaignCacheVideoKey] != nil) {
    if ([[data valueForKey:kUnityAdsCampaignCacheVideoKey] boolValue] != 0) {
      self.shouldCacheVideo = YES;
    }
  }
  
  self.bypassAppSheet = NO;
  if ([data objectForKey:kUnityAdsCampaignBypassAppSheet] != nil) {
    if ([[data valueForKey:kUnityAdsCampaignBypassAppSheet] boolValue] != 0) {
      self.bypassAppSheet = YES;
    }
  }
  
  self.expectedTrailerSize = -1;
  if ([data objectForKey:kUnityAdsCampaignExpectedFileSize] != nil) {
    if ([[data valueForKey:kUnityAdsCampaignExpectedFileSize] longLongValue] != 0) {
      self.expectedTrailerSize = [[data valueForKey:kUnityAdsCampaignExpectedFileSize] longLongValue];
    }
  }
  
  if (!failedData) {
    self.isValidCampaign = true;
  }

  NSString *customClickURLString = [data objectForKey:kUnityAdsCampaignCustomClickURLKey];
  if (customClickURLString != nil && [customClickURLString length] > 4) {
    UALOG_DEBUG(@"CustomClickUrl=%@ for CampaignID=%@", customClickURLString, idString);
    NSURL *customClickURL = [NSURL URLWithString:customClickURLString];
    self.customClickURL = customClickURL;
  }
  else {
    UALOG_DEBUG(@"Not a valid URL: %@", customClickURLString);
  }
  
  if (data[kUnityAdsCampaignURLSchemesKey] != nil) {
    self.urlSchemes = data[kUnityAdsCampaignURLSchemesKey];
  }
  
  self.allowStreaming = YES;
  if ([data objectForKey:kUnityAdsCampaignAllowStreamingKey] != nil) {
    if ([[data valueForKey:kUnityAdsCampaignAllowStreamingKey] boolValue] == 0) {
      self.allowStreaming = NO;
    }
  }
  
  NSString* filterMode = [data objectForKey:kUnityAdsCampaignFilterModeKey];
  if(filterMode != nil) {
    self.filterMode = filterMode;
  } else {
    self.filterMode = @"blacklist";
  }
  
  data = nil;
}

@end
