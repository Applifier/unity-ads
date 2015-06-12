package com.unity3d.ads.android.view;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.unity3d.ads.android.UnityAds;
import com.unity3d.ads.android.UnityAdsDeviceLog;
import com.unity3d.ads.android.campaign.UnityAdsCampaign.UnityAdsCampaignStatus;
import com.unity3d.ads.android.properties.UnityAdsConstants;
import com.unity3d.ads.android.properties.UnityAdsProperties;
import com.unity3d.ads.android.video.IUnityAdsVideoPlayerListener;
import com.unity3d.ads.android.video.UnityAdsVideoPlayView;
import com.unity3d.ads.android.webapp.IUnityAdsWebBridgeListener;
import com.unity3d.ads.android.webapp.IUnityAdsWebViewListener;
import com.unity3d.ads.android.webapp.UnityAdsWebBridge;
import com.unity3d.ads.android.webapp.UnityAdsWebData;
import com.unity3d.ads.android.webapp.UnityAdsWebData.UnityAdsVideoPosition;
import com.unity3d.ads.android.webapp.UnityAdsWebView;
import com.unity3d.ads.android.zone.UnityAdsZone;

import org.json.JSONObject;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class UnityAdsMainView extends RelativeLayout implements IUnityAdsWebViewListener, 
																IUnityAdsVideoPlayerListener {

	public static enum UnityAdsMainViewState { WebView, VideoPlayer };
	public static enum UnityAdsMainViewAction { VideoStart, VideoEnd, VideoSkipped, BackButtonPressed, RequestRetryVideoPlay };
	private static final int FILL_PARENT = -1;
	
	// Views
	public UnityAdsVideoPlayView videoplayerview = null;
	public UnityAdsWebView webview = null;

	// Listener
	private IUnityAdsMainViewListener _listener = null;
	private IUnityAdsWebBridgeListener _webBridgeListener = null;
	private UnityAdsMainViewState _currentState = UnityAdsMainViewState.WebView;

	
	public UnityAdsMainView(Context context, IUnityAdsMainViewListener listener, IUnityAdsWebBridgeListener webBridgeListener) {
		super(context);
		_listener = listener;
		_webBridgeListener = webBridgeListener;
		init();
	}
	
	
	public UnityAdsMainView(Context context) {
		super(context);
		init();
	}

	public UnityAdsMainView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public UnityAdsMainView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);		
		init();
	}
	
	
	/* PUBLIC METHODS */
	
	public void openAds (String view, JSONObject data) {
		if (UnityAdsProperties.getCurrentActivity() != null && UnityAdsProperties.getCurrentActivity() instanceof UnityAdsFullscreenActivity) {
			webview.setWebViewCurrentView(view, data);
			
			if (this.getParent() != null && (ViewGroup)this.getParent() != null)
				((ViewGroup)this.getParent()).removeView(this);
			
			if (this.getParent() == null)
				UnityAdsProperties.getCurrentActivity().addContentView(this, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));
			
			setViewState(UnityAdsMainViewState.WebView);
		}
		else {
			UnityAdsDeviceLog.error("Cannot open, wrong activity");
		}
	}

	public void fixActivityAttachment() {
		if (this.getParent() != null && (ViewGroup)this.getParent() != null)
			((ViewGroup)this.getParent()).removeView(this);

		UnityAdsProperties.getCurrentActivity().addContentView(this, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));
	}

	public void closeAds (JSONObject data) {		
		if (this.getParent() != null) {
			ViewGroup vg = (ViewGroup)this.getParent();
			if (vg != null)
				vg.removeView(this);
		}
		
		destroyVideoPlayerView();
		UnityAdsProperties.SELECTED_CAMPAIGN = null;
	}

	public void setViewState (UnityAdsMainViewState state) {
		if (!_currentState.equals(state)) {
			_currentState = state;
			
			switch (state) {
				case WebView:
					removeFromMainView(webview);
					addView(webview, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));
					focusToView(webview);
					break;
				case VideoPlayer:
					if (videoplayerview == null) {
						createVideoPlayerView();
						bringChildToFront(webview);
						focusToView(webview);
					}
					break;
			}
		}
	}
	
	public UnityAdsMainViewState getViewState () {
		return _currentState;
	}
	
	public void afterVideoPlaybackOperations () {
		if (videoplayerview != null) {
			videoplayerview.setKeepScreenOn(false);
		}
		
		destroyVideoPlayerView();
		setViewState(UnityAdsMainViewState.WebView);

		Activity currentActivity = UnityAdsProperties.getCurrentActivity();
		if(currentActivity != null) {
			currentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				onBackButtonClicked(this);
		    	return true;
		}
    	
    	return false;
    }
	
    protected void onAttachedToWindow() {
    	super.onAttachedToWindow();
    	focusToView(this);
    }
	
	/* PRIVATE METHODS */
	
	private void init () {
		UnityAdsDeviceLog.entered();   	
		this.setId(1001);
		createWebView();
	}
	
	private void destroyVideoPlayerView () {
		UnityAdsDeviceLog.entered();   	
		
		if (videoplayerview != null)
			videoplayerview.clearVideoPlayer();
		
		removeFromMainView(videoplayerview);
		videoplayerview = null;
	}
	
	private void createVideoPlayerView () {
		videoplayerview = new UnityAdsVideoPlayView(UnityAdsProperties.getCurrentActivity().getBaseContext(), this);
		videoplayerview.setLayoutParams(new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));
		videoplayerview.setId(1002);
		addView(videoplayerview);
	}
	
	private void createWebView () {
		webview = new UnityAdsWebView(UnityAdsProperties.getCurrentActivity(), this, new UnityAdsWebBridge(_webBridgeListener));
		webview.setId(1003);
		addView(webview, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));
	}
	
	private void removeFromMainView (View view) {
		if (view != null) {
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
			
			ViewGroup vg = (ViewGroup)view.getParent();
			if (vg != null)
				vg.removeView(view);
		}
	}
	
	private void focusToView (View view) {
		if (view != null) {
			view.setFocusable(true);
			view.setFocusableInTouchMode(true);
			view.requestFocus();
		}
	}
	
	private void sendActionToListener (UnityAdsMainViewAction action) {
		if (_listener != null) {
			_listener.onMainViewAction(action);
		}		
	}
	
	
	// IUnityAdsViewListener
	@Override
	public void onBackButtonClicked (View view) {
		UnityAdsDeviceLog.debug("Current state: " + _currentState.toString());
		
		if (videoplayerview != null) {
			UnityAdsDeviceLog.debug("Seconds: " + videoplayerview.getSecondsUntilBackButtonAllowed());
		}
		
		if ((UnityAdsProperties.SELECTED_CAMPAIGN != null &&
			UnityAdsProperties.SELECTED_CAMPAIGN.isViewed()) ||
			_currentState != UnityAdsMainViewState.VideoPlayer || 
			(_currentState == UnityAdsMainViewState.VideoPlayer && videoplayerview != null && videoplayerview.getSecondsUntilBackButtonAllowed() == 0) ||
			(_currentState == UnityAdsMainViewState.VideoPlayer && UnityAdsWebData.getZoneManager().getCurrentZone().disableBackButtonForSeconds() == 0)) {
			sendActionToListener(UnityAdsMainViewAction.BackButtonPressed);
		}
		else {
			UnityAdsDeviceLog.debug("Prevented back-button");
		}
	}
	
	// IUnityAdsVideoPlayerListener
	@Override
	public void onVideoPlaybackStarted () {
		UnityAdsDeviceLog.entered();
		
		JSONObject params = new JSONObject();
		JSONObject spinnerParams = new JSONObject();
		
		try {
			params.put(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_CAMPAIGNID_KEY, UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId());
			spinnerParams.put(UnityAdsConstants.UNITY_ADS_TEXTKEY_KEY, UnityAdsConstants.UNITY_ADS_TEXTKEY_BUFFERING);
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Could not create JSON");
		}
		
		sendActionToListener(UnityAdsMainViewAction.VideoStart);
		bringChildToFront(videoplayerview);
		
		// SENSOR_LANDSCAPE
		int targetOrientation = 6;
		
		if (Build.VERSION.SDK_INT < 9)
			targetOrientation = 0;
		
		UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if (currentZone.useDeviceOrientationForVideo()) {
			UnityAdsProperties.getCurrentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			
			// UNSPECIFIED
			targetOrientation = -1;
		}

		Activity currentActivity = UnityAdsProperties.getCurrentActivity();
		if(currentActivity != null) {
			currentActivity.setRequestedOrientation(targetOrientation);
		}

		focusToView(videoplayerview);

		if(webview != null) {
			webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_HIDESPINNER, spinnerParams);
			webview.setWebViewCurrentView(UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_COMPLETED, params);
		}
	}
	
	@Override
	public void onEventPositionReached (UnityAdsVideoPosition position) {
		if (UnityAdsProperties.SELECTED_CAMPAIGN != null && !UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignStatus().equals(UnityAdsCampaignStatus.VIEWED))
			UnityAds.webdata.sendCampaignViewProgress(UnityAdsProperties.SELECTED_CAMPAIGN, position);
	}
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		UnityAdsDeviceLog.entered();
		afterVideoPlaybackOperations();
		onEventPositionReached(UnityAdsVideoPosition.End);
		
		JSONObject params = new JSONObject();
		
		try {
			params.put(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_CAMPAIGNID_KEY, UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId());
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Could not create JSON");
		}
		
		webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_VIDEOCOMPLETED, params);
		sendActionToListener(UnityAdsMainViewAction.VideoEnd);
	}
	
	public void onVideoPlaybackError () {
		afterVideoPlaybackOperations();
		
		UnityAdsDeviceLog.entered();
		UnityAds.webdata.sendAnalyticsRequest(UnityAdsConstants.UNITY_ADS_ANALYTICS_EVENTTYPE_VIDEOERROR, UnityAdsProperties.SELECTED_CAMPAIGN);
		
		JSONObject errorParams = new JSONObject();
		JSONObject spinnerParams = new JSONObject();
		JSONObject params = new JSONObject();
		
		try {
			errorParams.put(UnityAdsConstants.UNITY_ADS_TEXTKEY_KEY, UnityAdsConstants.UNITY_ADS_TEXTKEY_VIDEOPLAYBACKERROR);
			spinnerParams.put(UnityAdsConstants.UNITY_ADS_TEXTKEY_KEY, UnityAdsConstants.UNITY_ADS_TEXTKEY_BUFFERING);
			params.put(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_CAMPAIGNID_KEY, UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId());
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Could not create JSON");
		}

		if(webview != null) {
			webview.setWebViewCurrentView(UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_COMPLETED, params);

			webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_SHOWERROR, errorParams);
			webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_VIDEOCOMPLETED, params);
			webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_HIDESPINNER, spinnerParams);
		}

		if(UnityAdsProperties.SELECTED_CAMPAIGN != null) {
			UnityAdsProperties.SELECTED_CAMPAIGN.setCampaignStatus(UnityAdsCampaignStatus.VIEWED);
			UnityAdsProperties.SELECTED_CAMPAIGN = null;
		}
	}
	
	public void onVideoSkip () {
		afterVideoPlaybackOperations();
		JSONObject params = new JSONObject();
		
		try {
			params.put(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_CAMPAIGNID_KEY, UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId());
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Could not create JSON");
		}
		
		webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_VIDEOCOMPLETED, params);
		sendActionToListener(UnityAdsMainViewAction.VideoSkipped);
	}

	// Almost like onVideoSkip but this is a bit different situation
	// This covers situations where user has e.g. pressed home button and hidden the video instead of pressing skip button
	public void onVideoHidden() {
		if (videoplayerview != null) {
			videoplayerview.setKeepScreenOn(false);
			videoplayerview.hideVideo();
			videoplayerview = null;
		}

		setViewState(UnityAdsMainViewState.WebView);
		
		Activity currentActivity = UnityAdsProperties.getCurrentActivity();
		if(currentActivity != null) {
			currentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}

		JSONObject params = new JSONObject();
		try {
			params.put(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_CAMPAIGNID_KEY, UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId());
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Could not create JSON");
		}
		webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_VIDEOCOMPLETED, params);

		sendActionToListener(UnityAdsMainViewAction.VideoSkipped);
	}

	// IUnityAdsWebViewListener
	@Override
	public void onWebAppLoaded () {
		webview.initWebApp(UnityAds.webdata.getData());
	}
}