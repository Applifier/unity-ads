//
//  UnityAdsWebAppController.h
//  UnityAds
//
//  Created by bluesun on 10/23/12.
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@protocol UnityAdsWebAppControllerDelegate <NSObject>

@required
- (void)webAppReady;
@end

@interface UnityAdsWebAppController : NSObject <UIWebViewDelegate, UIScrollViewDelegate>

@property (nonatomic, strong) UIWebView* webView;
@property (nonatomic, assign) BOOL webViewLoaded;
@property (nonatomic, assign) BOOL webViewInitialized;
@property (nonatomic, weak) id<UnityAdsWebAppControllerDelegate> delegate;

- (void)setWebViewCurrentView:(NSString *)view data:(NSDictionary *)data;
- (void)loadWebApp:(NSDictionary *)webAppParams;
- (void)setupWebApp:(CGRect)frame;
- (void)openExternalUrl:(NSString *)urlString;
- (void)handleWebEvent:(NSString *)type data:(NSDictionary *)data;
- (void)sendNativeEventToWebApp:(NSString *)eventType data:(NSDictionary *)data;
- (void)initWebApp;

+ (instancetype)sharedInstance;
@end
