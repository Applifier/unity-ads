//
//  UnityAdsCampaign.h
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface UnityAdsCampaign : NSObject

@property (nonatomic, strong) NSURL *endScreenURL;
@property (nonatomic, strong) NSURL *endScreenPortraitURL;
@property (nonatomic, strong) NSURL *clickURL;
@property (nonatomic, strong) NSURL *pictureURL;
@property (nonatomic, strong) NSURL *trailerDownloadableURL;
@property (nonatomic, strong) NSURL *trailerStreamingURL;
@property (nonatomic, strong) NSURL *gameIconURL;
@property (nonatomic, strong) NSURL *customClickURL;
@property (nonatomic, strong) NSString *gameID;
@property (nonatomic, strong) NSString *gameName;
@property (nonatomic, strong) NSString *id;
@property (nonatomic, strong) NSString *tagLine;
@property (nonatomic, strong) NSString *itunesID;
@property (nonatomic, assign) BOOL shouldCacheVideo;
@property (nonatomic, assign) BOOL allowedToCacheVideo;
@property (nonatomic, assign) BOOL viewed;
@property (nonatomic, assign) BOOL bypassAppSheet;
@property (nonatomic, assign) long long expectedTrailerSize;
@property (nonatomic, assign) BOOL isValidCampaign;
@property (nonatomic, assign) long long videoCachingStartTime;
@property (nonatomic, assign) long long videoCachingEndTime;
@property (nonatomic, assign) long long videoBufferingStartTime;
@property (nonatomic, assign) long long videoBufferingEndTime;
@property (nonatomic, assign) BOOL nativeTrackingQuerySent;
@property (nonatomic, strong) NSArray * urlSchemes;
@property (nonatomic, assign) BOOL allowStreaming;
@property (nonatomic, strong) NSString *filterMode;

- (id)initWithData:(NSDictionary *)data;
- (long long)geBufferingDuration;

@end
