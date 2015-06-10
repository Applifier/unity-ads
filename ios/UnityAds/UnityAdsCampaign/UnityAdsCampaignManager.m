//
//  UnityAdsCampaignManager.m
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import "UnityAdsCampaignManager.h"
#import "UnityAdsCampaign.h"
#import "UnityAdsRewardItem.h"
#import "UnityAds.h"
#import "UnityAdsSBJsonParser.h"
#import "UnityAdsCacheManager.h"
#import "NSObject+UnityAdsSBJson.h"
#import "UnityAdsProperties.h"
#import "UnityAdsConstants.h"
#import "UnityAdsZoneParser.h"
#import "UnityAdsZoneManager.h"

@interface UnityAdsCampaignManager () <NSURLConnectionDelegate>
@property (nonatomic, strong) NSURLConnection *urlConnection;
@property (nonatomic, strong) NSMutableData *campaignDownloadData;
@property (nonatomic, strong) NSOperationQueue *installedAppsQueue;
@property (nonatomic, assign) BOOL installedAppsSent;
@end

@implementation UnityAdsCampaignManager

@synthesize campaignDownloadData = _campaignDownloadData;

static UnityAdsCampaignManager *sharedUnityAdsInstanceCampaignManager = nil;

+ (id)sharedInstance {
	@synchronized(self) {
		if (sharedUnityAdsInstanceCampaignManager == nil)
      sharedUnityAdsInstanceCampaignManager = [[UnityAdsCampaignManager alloc] init];
	}
	
	return sharedUnityAdsInstanceCampaignManager;
}

- (id)init {
  if (self = [super init]) {
    [self setInstalledAppsSent:FALSE];
  }
  return self;
}

#pragma mark - Private

+ (BOOL)isInstalled:(NSArray *)urlSchemes {
  __block int matchesCount = 0;
  [urlSchemes enumerateObjectsUsingBlock:^(NSString * urlScheme, NSUInteger idx, BOOL *stop) {
    if ([[UIApplication sharedApplication] canOpenURL:[NSURL URLWithString:[NSString stringWithFormat:@"%@://", urlScheme]]]) {
      matchesCount++;
    }
  }];
  return matchesCount == urlSchemes.count && urlSchemes.count;
}

+ (NSArray *)installedApps:(NSArray *)urlSchemeMap {
  __block NSMutableArray * installedApps = [NSMutableArray new];
  [urlSchemeMap enumerateObjectsUsingBlock:^(NSDictionary *urlSchemesEntry, NSUInteger idx, BOOL *stop) {
    if ([UnityAdsCampaignManager isInstalled:[urlSchemesEntry objectForKey:@"schemes"]]) {
      [installedApps addObject:@{@"id": [urlSchemesEntry objectForKey:@"id"]}];
    }
  }];
  return [installedApps copy];
}

- (void)_campaignDataReceived {
  [self _processCampaignDownloadData];
}

- (NSArray *)deserializeCampaigns:(NSArray *)campaignArray {
	if (campaignArray == nil || [campaignArray count] == 0) {
		UALOG_DEBUG(@"Input empty or nil.");
		return nil;
	}
	
	NSMutableArray *campaigns = [NSMutableArray array];
	
	for (id campaignDictionary in campaignArray) {
		if ([campaignDictionary isKindOfClass:[NSDictionary class]]) {
			UnityAdsCampaign *campaign = [[UnityAdsCampaign alloc] initWithData:campaignDictionary];
      if (campaign.isValidCampaign) {
        NSString* appFiltering = [[UnityAdsProperties sharedInstance] appFiltering];
        if([appFiltering isEqualToString:@"simple"] || [appFiltering isEqualToString:@"advanced"]) {
          if([UnityAdsCampaignManager isInstalled:campaign.urlSchemes]) {
            if([campaign.filterMode isEqualToString:@"blacklist"]) {
              UALOG_DEBUG(@"Blacklisted installed game %@", campaign.gameID);
            } else if([campaign.filterMode isEqualToString:@"whitelist"]) {
              UALOG_DEBUG(@"Whitelisted installed game %@", campaign.gameID);
              [campaigns addObject:campaign];
            }
            [[[UnityAdsProperties sharedInstance] installedApps] addObject:campaign.gameID];
          } else {
            [campaigns addObject:campaign];
          }
        } else {
          [campaigns addObject:campaign];
        }
      }
		}
		else {
			UALOG_DEBUG(@"Unexpected value in campaign dictionary list. %@, %@", [campaignDictionary class], campaignDictionary);
			continue;
		}
	}
	return campaigns;
}

- (void)_processCampaignDownloadData {
  
  if (self.campaignDownloadData == nil) {
    dispatch_async(dispatch_get_main_queue(), ^(void) {
      [self.delegate campaignManagerCampaignDataFailed];
    });
    UALOG_DEBUG(@"Campaign download data is NULL!");
    return;
  }
  
  NSString *jsonString = [[NSString alloc] initWithData:self.campaignDownloadData encoding:NSUTF8StringEncoding];
  _campaignData = [jsonString JSONValue];
  
  if (_campaignData == nil) {
    dispatch_async(dispatch_get_main_queue(), ^(void) {
      [self.delegate campaignManagerCampaignDataFailed];
    });
    UALOG_DEBUG(@"Campaigndata is NULL!");
    return;
  }
  
  [[[UnityAdsProperties sharedInstance] installedApps] removeAllObjects];
  
  UALOG_DEBUG(@"%@", [_campaignData JSONRepresentation]);
	UAAssert([_campaignData isKindOfClass:[NSDictionary class]]);
	
  if (_campaignData != nil && [_campaignData isKindOfClass:[NSDictionary class]]) {
    NSDictionary *jsonDictionary = [(NSDictionary *)_campaignData objectForKey:kUnityAdsJsonDataRootKey];
    BOOL validData = YES;
    
    if ([jsonDictionary objectForKey:kUnityAdsWebViewUrlKey] == nil) validData = NO;
    if ([jsonDictionary objectForKey:kUnityAdsAnalyticsUrlKey] == nil) validData = NO;
    if ([jsonDictionary objectForKey:kUnityAdsUrlKey] == nil) validData = NO;
    if ([jsonDictionary objectForKey:kUnityAdsGamerIDKey] == nil) validData = NO;
    if ([jsonDictionary objectForKey:kUnityAdsCampaignsKey] == nil) validData = NO;
    if ([jsonDictionary objectForKey:kUnityAdsZonesRootKey] == nil) validData = NO;
    
    id zoneManager = [UnityAdsZoneManager sharedInstance];
    [zoneManager clearZones];
    int addedZones = [zoneManager addZones:[UnityAdsZoneParser parseZones:[jsonDictionary objectForKey:kUnityAdsZonesRootKey]]];
    if(addedZones == 0) validData = NO;
    
    NSString * appFiltering = (NSString *)[jsonDictionary objectForKey:kUnityAdsAppFilteringKey];
    if(appFiltering != nil && [appFiltering length] > 0) {
      [[UnityAdsProperties sharedInstance] setAppFiltering:appFiltering];
    }
    
    NSString * urlSchemeMapString = (NSString *)[jsonDictionary objectForKey:kUnityAdsUrlSchemeMapKey];
    if(urlSchemeMapString != nil && [urlSchemeMapString length] > 0) {
      [[UnityAdsProperties sharedInstance] setUrlSchemeMap:urlSchemeMapString];
    }
    
    NSString * installedAppsUrlString = (NSString *)[jsonDictionary objectForKey:kUnityAdsInstalledAppsUrlKey];
    if(installedAppsUrlString != nil && [installedAppsUrlString length] > 0) {
      [[UnityAdsProperties sharedInstance] setInstalledAppsUrl:installedAppsUrlString];
    }
    
    if([[[UnityAdsProperties sharedInstance] appFiltering] isEqualToString:@"advanced"]) {
      [self _sendInstalledApps];
    }
    
    self.campaigns = [self deserializeCampaigns:[jsonDictionary objectForKey:kUnityAdsCampaignsKey]];
    if (self.campaigns == nil || [self.campaigns count] == 0) validData = NO;
    
    if (validData) {
      [[UnityAdsProperties sharedInstance] setWebViewBaseUrl:(NSString *)[jsonDictionary objectForKey:kUnityAdsWebViewUrlKey]];
      [[UnityAdsProperties sharedInstance] setAnalyticsBaseUrl:(NSString *)[jsonDictionary objectForKey:kUnityAdsAnalyticsUrlKey]];
      [[UnityAdsProperties sharedInstance] setAdsBaseUrl:(NSString *)[jsonDictionary objectForKey:kUnityAdsUrlKey]];
      
      if ([jsonDictionary objectForKey:kUnityAdsSdkVersionKey] != nil &&
          [[jsonDictionary objectForKey:kUnityAdsSdkVersionKey] isKindOfClass:[NSString class]]) {
        [[UnityAdsProperties sharedInstance] setExpectedSdkVersion:[jsonDictionary objectForKey:kUnityAdsSdkVersionKey]];
        UALOG_DEBUG(@"Got SDK Version: %@", [[UnityAdsProperties sharedInstance] expectedSdkVersion]);
      }
      
      if ([jsonDictionary objectForKey:kUnityAdsWebViewDataParamSdkIsCurrentKey] != nil) {
        [[UnityAdsProperties sharedInstance] setSdkIsCurrent:[[jsonDictionary objectForKey:kUnityAdsWebViewDataParamSdkIsCurrentKey] boolValue]];
      }
      
      NSString *gamerId = [jsonDictionary objectForKey:kUnityAdsGamerIDKey];
      
      [[UnityAdsProperties sharedInstance] setGamerId:gamerId];
      
      [self.campaigns enumerateObjectsUsingBlock:^(UnityAdsCampaign *campaign, NSUInteger idx, BOOL *stop) {
        if ((campaign.shouldCacheVideo && campaign.allowedToCacheVideo) || (campaign.allowedToCacheVideo && idx == 0)) {
          [[UnityAdsCacheManager sharedInstance] cache:ResourceTypeTrailerVideo forCampaign:campaign];
        }
      }];
      
      NSLog(@"Unity Ads initialized with %lu campaigns and %d zones", (unsigned long)[self.campaigns count], addedZones);
      
      dispatch_async(dispatch_get_main_queue(), ^(void) {
        [self.delegate campaignManagerCampaignDataReceived];
      });
    }
    else {
      dispatch_async(dispatch_get_main_queue(), ^(void) {
        [self.delegate campaignManagerCampaignDataFailed];
      });
    }
  }
  
  self.campaignDownloadData = nil;
}

- (NSArray *)_parseUrlSchemeMap:(NSString *)urlSchemeMapString {
  NSDictionary *urlSchemeMap = [urlSchemeMapString JSONValue];
  return (NSArray *)[urlSchemeMap objectForKey:@"urlSchemes"];
}

- (void)_sendInstalledApps {
  if([self installedAppsSent]) return;
  [self setInstalledAppsSent:TRUE];
  [self setInstalledAppsQueue:[NSOperationQueue new]];
  NSURLRequest *urlSchemeMapRequest = [[NSURLRequest alloc] initWithURL:[NSURL URLWithString:[UnityAdsProperties sharedInstance].urlSchemeMap] cachePolicy:NSURLRequestReloadIgnoringLocalAndRemoteCacheData timeoutInterval:60];
  [NSURLConnection sendAsynchronousRequest:urlSchemeMapRequest queue:[self installedAppsQueue] completionHandler:^(NSURLResponse *response, NSData *data, NSError *connectionError) {
    if(data == nil) {
      UALOG_DEBUG(@"Failed to receive url scheme map");
      return;
    }
    
    NSArray *urlSchemeMap = [self _parseUrlSchemeMap:[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]];
    if(urlSchemeMap != nil) {
      NSArray *installedApps = [UnityAdsCampaignManager installedApps:urlSchemeMap];
      NSDictionary *installedAppsDict = @{@"games": installedApps};
      
      if([installedApps count] > 0) {
        NSURL *installedAppsUrl = [NSURL URLWithString:[NSString stringWithFormat:@"%@%@", [UnityAdsProperties sharedInstance].installedAppsUrl, [[UnityAdsProperties sharedInstance] createCampaignQueryString:FALSE]]];
        NSMutableURLRequest *installedAppsRequest = [[NSMutableURLRequest alloc] initWithURL:installedAppsUrl cachePolicy:NSURLRequestReloadIgnoringLocalAndRemoteCacheData timeoutInterval:60];
        [installedAppsRequest setHTTPMethod:@"POST"];
        [installedAppsRequest setHTTPBody:[[installedAppsDict JSONRepresentation] dataUsingEncoding:NSUTF8StringEncoding]];
        [installedAppsRequest setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
        [NSURLConnection sendAsynchronousRequest:installedAppsRequest queue:[self installedAppsQueue] completionHandler:^(NSURLResponse *response, NSData *data, NSError *connectionError) {
          if(data == nil) {
            UALOG_DEBUG(@"Error sending installed apps");
          } else {
            UALOG_DEBUG(@"Sent installed apps successfully");
          }
        }];
      }
    }
  }];
}

#pragma mark - Public

- (void)updateCampaigns {
	UAAssert(![NSThread isMainThread]);
	
	NSString *urlString = [[UnityAdsProperties sharedInstance] campaignDataUrl];
	
  [[UnityAdsProperties sharedInstance] refreshCampaignQueryString];
  if ([[UnityAdsProperties sharedInstance] campaignQueryString] != nil)
		urlString = [urlString stringByAppendingString:[[UnityAdsProperties sharedInstance] campaignQueryString]];
  
  NSLog(@"Requesting Unity Ads ad plan from %@", urlString);
	NSURLRequest *request = [[NSURLRequest alloc] initWithURL:[NSURL URLWithString:urlString] cachePolicy:NSURLRequestReloadIgnoringLocalAndRemoteCacheData timeoutInterval:60];
	self.urlConnection = [[NSURLConnection alloc] initWithRequest:request delegate:self startImmediately:NO];
	[self.urlConnection start];
}

- (NSURL *)getVideoURLForCampaign:(UnityAdsCampaign *)campaign {
	@synchronized (self) {
		if (campaign == nil) {
			UALOG_DEBUG(@"Input is nil.");
			return nil;
		}
    
    UnityAdsCacheManager * cacheManager = [UnityAdsCacheManager sharedInstance];
		
		NSURL *videoURL = [cacheManager localURLFor:ResourceTypeTrailerVideo ofCampaign:campaign];
    if ([cacheManager campaignExistsInQueue:campaign withResourceType:ResourceTypeTrailerVideo]) {
      UALOG_DEBUG(@"Cancel caching video for campaign %@", campaign.id);
      [cacheManager cancelAllDownloads];
    }
		if (![cacheManager is:ResourceTypeTrailerVideo cachedForCampaign:campaign])
    {
      UALOG_DEBUG(@"Choosing streaming URL for campaign %@", campaign.id);
      videoURL = campaign.trailerStreamingURL;
    } else {
      UALOG_DEBUG(@"Choosing trailer URL for campaign %@", campaign.id);
    }
		return videoURL;
	}
}

- (void)cacheNextCampaignAfter:(UnityAdsCampaign *)currentCampaign {
  @synchronized(self.campaigns) {
    __block NSUInteger currentIndex = 0;
    [self.campaigns enumerateObjectsUsingBlock:^(UnityAdsCampaign *campaign, NSUInteger idx, BOOL *stop) {
      if ([campaign.id isEqualToString:currentCampaign.id]) {
        currentIndex = idx + 1;
        *stop = YES;
      }
    }];
    
    if (currentIndex <= self.campaigns.count - 1) {
      [[UnityAdsCacheManager sharedInstance] cache:ResourceTypeTrailerVideo forCampaign:self.campaigns[currentIndex]];
    }
  }
}

- (UnityAdsCampaign *)getCampaignWithId:(NSString *)campaignId {
	UALOG_DEBUG(@"");
	UAAssertV([NSThread isMainThread], nil);
	UnityAdsCampaign *foundCampaign = nil;
	
	for (UnityAdsCampaign *campaign in self.campaigns) {
		if ([campaign.id isEqualToString:campaignId]) {
			foundCampaign = campaign;
			break;
		}
	}
	
	return foundCampaign;
}

- (UnityAdsCampaign *)getCampaignWithITunesId:(NSString *)iTunesId {
	UALOG_DEBUG(@"");
	UAAssertV([NSThread isMainThread], nil);
	UnityAdsCampaign *foundCampaign = nil;
	
	for (UnityAdsCampaign *campaign in self.campaigns) {
		if ([campaign.itunesID isEqualToString:iTunesId]) {
			foundCampaign = campaign;
			break;
		}
	}
	
	return foundCampaign;
}

- (UnityAdsCampaign *)getCampaignWithClickUrl:(NSString *)clickUrl {
	UALOG_DEBUG(@"");
	UAAssertV([NSThread isMainThread], nil);
	UnityAdsCampaign *foundCampaign = nil;
	
	for (UnityAdsCampaign *campaign in self.campaigns) {
		if ([[campaign.clickURL absoluteString] isEqualToString:clickUrl]) {
			foundCampaign = campaign;
			break;
		}
	}
	
	return foundCampaign;
}

- (NSArray *)getViewableCampaigns {
  NSMutableArray *retAr = [[NSMutableArray alloc] init];
  
  if (self.campaigns != nil) {
    for (UnityAdsCampaign* campaign in self.campaigns) {
      BOOL inCache = [[UnityAdsCacheManager sharedInstance] is:ResourceTypeTrailerVideo cachedForCampaign:campaign];
      if (!campaign.viewed && (inCache || campaign.allowStreaming)) {
        [retAr addObject:campaign];
      }
    }
  }
  
  return retAr;
}

- (void)cancelAllDownloads {
	UAAssert(![NSThread isMainThread]);
	
	[self.urlConnection cancel];
	self.urlConnection = nil;
	
	[[UnityAdsCacheManager sharedInstance] cancelAllDownloads];
}

- (void)dealloc {
}

#pragma mark - NSURLConnectionDelegate

- (void)connection:(NSURLConnection *)connection didReceiveResponse:(NSURLResponse *)response {
  self.campaignDownloadData = [NSMutableData data];
}

- (void)connection:(NSURLConnection *)connection didReceiveData:(NSData *)data {
	[self.campaignDownloadData appendData:data];
}

- (void)connectionDidFinishLoading:(NSURLConnection *)connection {
  [self _campaignDataReceived];
}

static int retryCount = 0;
- (void)connection:(NSURLConnection *)connection didFailWithError:(NSError *)error {
	self.campaignDownloadData = nil;
	self.urlConnection = nil;
	
	if(retryCount < kUnityAdsWebDataMaxRetryCount) {
    ++retryCount;
    UALOG_DEBUG(@"Retrying campaign download in %d seconds.", kUnityAdsWebDataRetryInterval);
    [NSTimer scheduledTimerWithTimeInterval:kUnityAdsWebDataRetryInterval target:self selector:@selector(updateCampaigns) userInfo:nil repeats:NO];
  } else {
    UALOG_DEBUG(@"Not retrying campaign download.");
    dispatch_async(dispatch_get_main_queue(), ^{
      [self.delegate campaignManagerCampaignDataFailed];
    });
  }
}


#pragma mark - UnityAdsCacheDelegate

- (void)cache:(UnityAdsCacheManager *)cacheManager finishedCachingCampaign:(UnityAdsCampaign *)campaign {
  dispatch_async(dispatch_get_main_queue(), ^{
		[self.delegate campaignManager:self updatedWithCampaigns:self.campaigns gamerID:[[UnityAdsProperties sharedInstance] gamerId]];
	});
}

@end
