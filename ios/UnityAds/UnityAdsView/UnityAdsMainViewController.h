//
//  UnityAdsAdViewController.h
//  UnityAds
//
//  Created by bluesun on 11/21/12.
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import <UIKit/UIKit.h>

#import "../UnityAdsProperties/UnityAdsConstants.h"
#import "../UnityAdsViewState/UnityAdsViewState.h"

@protocol UnityAdsMainViewControllerDelegate <NSObject>

@required
- (void)mainControllerWillOpen;
- (void)mainControllerDidOpen;
- (void)mainControllerWillClose;
- (void)mainControllerDidClose;
- (void)mainControllerStartedPlayingVideo;
- (void)mainControllerVideoEnded;
- (void)mainControllerVideoSkipped;
- (void)mainControllerWillLeaveApplication;
@end

@interface UnityAdsMainViewController : UIViewController <UnityAdsViewStateDelegate>

@property (nonatomic, weak) id<UnityAdsMainViewControllerDelegate> delegate;
@property (nonatomic, assign) BOOL isClosing;
@property (nonatomic, assign) BOOL isOpen;


+ (instancetype)sharedInstance;

- (BOOL)openAds:(BOOL)animated inState:(UnityAdsViewStateType)requestedState withOptions:(NSDictionary *)options;
- (BOOL)closeAds:(BOOL)forceMainThread withAnimations:(BOOL)animated withOptions:(NSDictionary *)options;
- (BOOL)changeState:(UnityAdsViewStateType)requestedState withOptions:(NSDictionary *)options;

- (BOOL)mainControllerVisible;
- (void)applyOptionsToCurrentState:(NSDictionary *)options;
- (void)applyViewStateHandler:(UnityAdsViewState *)viewState;
- (UnityAdsViewState *)getCurrentViewState;
- (UnityAdsViewState *)getPreviousViewState;
@end
