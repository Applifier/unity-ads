package com.unity3d.ads.android;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.unity3d.ads.android.cache.UnityAdsCacheManager;
import com.unity3d.ads.android.cache.UnityAdsDownloader;
import com.unity3d.ads.android.cache.IUnityAdsCacheListener;
import com.unity3d.ads.android.campaign.UnityAdsCampaign;
import com.unity3d.ads.android.campaign.UnityAdsCampaign.UnityAdsCampaignStatus;
import com.unity3d.ads.android.campaign.UnityAdsCampaignHandler;
import com.unity3d.ads.android.data.UnityAdsAdvertisingId;
import com.unity3d.ads.android.item.UnityAdsRewardItem;
import com.unity3d.ads.android.item.UnityAdsRewardItemManager;
import com.unity3d.ads.android.properties.UnityAdsConstants;
import com.unity3d.ads.android.properties.UnityAdsProperties;
import com.unity3d.ads.android.video.UnityAdsVideoPausedView;
import com.unity3d.ads.android.view.UnityAdsFullscreenActivity;
import com.unity3d.ads.android.view.UnityAdsMainView;
import com.unity3d.ads.android.view.UnityAdsMainView.UnityAdsMainViewAction;
import com.unity3d.ads.android.view.UnityAdsMainView.UnityAdsMainViewState;
import com.unity3d.ads.android.view.IUnityAdsMainViewListener;
import com.unity3d.ads.android.webapp.UnityAdsWebData;
import com.unity3d.ads.android.webapp.IUnityAdsWebBridgeListener;
import com.unity3d.ads.android.webapp.IUnityAdsWebDataListener;
import com.unity3d.ads.android.zone.UnityAdsIncentivizedZone;
import com.unity3d.ads.android.zone.UnityAdsZone;
import com.unity3d.ads.android.zone.UnityAdsZoneManager;

import android.os.SystemClock;


@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class UnityAds implements IUnityAdsCacheListener,
										IUnityAdsWebDataListener,
										IUnityAdsWebBridgeListener,
										IUnityAdsMainViewListener {

	// Reward item HashMap keys
	public static final String UNITY_ADS_REWARDITEM_PICTURE_KEY = "picture";
	public static final String UNITY_ADS_REWARDITEM_NAME_KEY = "name";

	// Unity Ads developer options keys
	public static final String UNITY_ADS_OPTION_NOOFFERSCREEN_KEY = "noOfferScreen";
	public static final String UNITY_ADS_OPTION_OPENANIMATED_KEY = "openAnimated";
	public static final String UNITY_ADS_OPTION_GAMERSID_KEY = "sid";
	public static final String UNITY_ADS_OPTION_MUTE_VIDEO_SOUNDS = "muteVideoSounds";
	public static final String UNITY_ADS_OPTION_VIDEO_USES_DEVICE_ORIENTATION = "useDeviceOrientationForVideo";

	// Unity Ads components
	public static UnityAdsCacheManager cachemanager = null;
	public static UnityAdsWebData webdata = null;
	public static UnityAdsMainView mainview = null;

	// Temporary data
	private static boolean _initialized = false;
	private static boolean _showingAds = false;
	private static boolean _adsReadySent = false;
	private static boolean _webAppLoaded = false;
	private static boolean _openRequestFromDeveloper = false;
	private static boolean _refreshAfterShowAds = false;
	private static boolean _fixMainview = false;
	private static boolean _preventVideoDoubleStart = false;
	private static boolean _singleTaskApplication = false;
	private static boolean _hidingHandled = false;
	private static AlertDialog _alertDialog = null;

	private static TimerTask _pauseScreenTimer = null;
	private static Timer _pauseTimer = null;
	private static TimerTask _campaignRefreshTimerTask = null;
	private static Timer _campaignRefreshTimer = null;
	private static long _campaignRefreshTimerDeadline = 0;

	private static UnityAds _instance = null;

	// Listeners
	private static IUnityAdsListener _adsListener = null;

	private UnityAds () {
	}

	/* PUBLIC STATIC METHODS */

	public static boolean isSupported () {
		if (Build.VERSION.SDK_INT < 9) {
			return false;
		}

		return true;
	}

	public static void setDebugMode(boolean debugModeEnabled) {
		if(debugModeEnabled) {
			UnityAdsDeviceLog.setLogLevel(UnityAdsDeviceLog.LOGLEVEL_DEBUG);
		} else {
			UnityAdsDeviceLog.setLogLevel(UnityAdsDeviceLog.LOGLEVEL_INFO);
		}
	}

	public static void setTestMode (boolean testModeEnabled) {
		UnityAdsProperties.TESTMODE_ENABLED = testModeEnabled;
	}

	public static void setTestDeveloperId (String testDeveloperId) {
		UnityAdsProperties.TEST_DEVELOPER_ID = testDeveloperId;
	}

	public static void setTestOptionsId (String testOptionsId) {
		UnityAdsProperties.TEST_OPTIONS_ID = testOptionsId;
	}

	public static String getSDKVersion () {
		return UnityAdsConstants.UNITY_ADS_VERSION;
	}

	public static void setCampaignDataURL (String campaignDataURL) {
		UnityAdsProperties.CAMPAIGN_DATA_URL = campaignDataURL;
	}

	public static void enableUnityDeveloperInternalTestMode() {
		UnityAdsProperties.CAMPAIGN_DATA_URL = "https://impact.staging.applifier.com/mobile/campaigns";
		UnityAdsProperties.UNITY_DEVELOPER_INTERNAL_TEST = true;
	}

	/* PUBLIC METHODS */

	public static void setListener (IUnityAdsListener listener) {
		_adsListener = listener;
	}

	public static void changeActivity (Activity activity) {
		if (activity == null) {
			UnityAdsDeviceLog.debug("changeActivity: null, ignoring");
			return;
		}

		UnityAdsDeviceLog.debug("changeActivity: " + activity.getClass().getName());

		if (activity != null && !activity.equals(UnityAdsProperties.getCurrentActivity())) {
			UnityAdsProperties.CURRENT_ACTIVITY = new WeakReference<Activity>(activity);

			// Not the most pretty way to detect when the fullscreen activity is ready
			if (activity != null && activity instanceof UnityAdsFullscreenActivity) {
				String view = null;

				if (_openRequestFromDeveloper) {
					view = UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_START;
					UnityAdsDeviceLog.debug("This open request is from the developer, setting start view");
				}

				if (view != null) {
					open(view);
				}

				_openRequestFromDeveloper = false;
			}
			else {
				UnityAdsProperties.BASE_ACTIVITY = new WeakReference<Activity>(activity);
			}
		}
	}

	public static boolean hide () {
		if (_showingAds) {
			close();
			return true;
		}

		return false;
	}

	public static boolean setZone(String zoneId) {
		if(!_showingAds) {
			if(UnityAdsWebData.getZoneManager() == null) {
				throw new IllegalStateException("Unable to set zone before campaigns are available");
			}

			return UnityAdsWebData.getZoneManager().setCurrentZone(zoneId);
		}
		return false;
	}

	public static boolean setZone(String zoneId, String rewardItemKey) {
		if(!_showingAds && setZone(zoneId)) {
			UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
			if(currentZone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)currentZone).itemManager();
				return itemManager.setCurrentItem(rewardItemKey);
			}
		}
		return false;
	}

	public static String getZone() {
		UnityAdsZoneManager zoneManager = UnityAdsWebData.getZoneManager();

		if(zoneManager != null) {
			UnityAdsZone currentZone = zoneManager.getCurrentZone();

			if(currentZone != null) {
				return currentZone.getZoneId();
			}
		}

		return null;
	}

	public static boolean show (Map<String, Object> options) {
		if (canShow()) {
			UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();

			if (currentZone != null) {
				UnityAdsDownloader.stopAllDownloads();

				currentZone.mergeOptions(options);

				if (currentZone.noOfferScreen()) {
					ArrayList<UnityAdsCampaign> viewableCampaigns = webdata.getViewableVideoPlanCampaigns();

					if (viewableCampaigns.size() > 0) {
						UnityAdsCampaign selectedCampaign = viewableCampaigns.get(0);
						UnityAdsProperties.SELECTED_CAMPAIGN = selectedCampaign;
					}
				}

				UnityAdsDeviceLog.info("Launching ad from \"" + currentZone.getZoneName() + "\", options: " + currentZone.getZoneOptions().toString());

				_openRequestFromDeveloper = true;
				_showingAds = true;
				_preventVideoDoubleStart = false;
				_hidingHandled = false;
				UnityAdsProperties.SELECTED_CAMPAIGN_CACHED = false;
				startFullscreenActivity();
				return _showingAds;
			} else {
				UnityAdsDeviceLog.error("Unity Ads current zone is null");
			}
		} else {
			UnityAdsDeviceLog.error("Unity Ads not ready to show ads");
		}

		return false;
	}

	public static boolean show () {
		return show(null);
	}

	public static boolean canShowAds () {
		return canShow();
	}

	// Replacement method for old internal uses of canShowAds
	private static boolean hasViewableAds() {
		return webdata != null &&
			webdata.getViewableVideoPlanCampaigns() != null &&
			webdata.getViewableVideoPlanCampaigns().size() > 0;
	}

	public static boolean canShow () {
		if(webdata == null) {
			logCanShow(1);
			return false;
		}

		if(!_webAppLoaded) {
			logCanShow(2);
			return false;
		}

		if(_showingAds) {
			logCanShow(3);
			return false;
		}

		Activity currentActivity = UnityAdsProperties.getCurrentActivity();
		if(currentActivity != null) {
			ConnectivityManager cm = (ConnectivityManager)currentActivity.getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);

			if(cm != null) {
				NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
				boolean isConnected = activeNetwork != null && activeNetwork.isConnected();

				if(!isConnected) {
					logCanShow(4);
					return false;
				}
			}
		}

		if(webdata.initInProgress()) return false;

		ArrayList<UnityAdsCampaign> viewableCampaigns = webdata.getViewableVideoPlanCampaigns();

		if(viewableCampaigns == null) {
			logCanShow(5);
			return false;
		}

		if(viewableCampaigns.size() == 0) {
			logCanShow(6);
			return false;
		}

		UnityAdsCampaign nextCampaign = viewableCampaigns.get(0);
		if(!nextCampaign.allowStreamingVideo().booleanValue()) {
			if(!cachemanager.isCampaignCached(nextCampaign, true)) {
				logCanShow(7);
				return false;
			}
		}

		logCanShow(0);
		return true;
	}

	private static int prevCanShowLogMsg = -1;
	private static final String[] canShowLogMsgs = {
		"Unity Ads is ready to show ads",
		"Unity Ads not ready to show ads: not initialized",
		"Unity Ads not ready to show ads: webapp not initialized",
		"Unity Ads not ready to show ads: already showing ads",
		"Unity Ads not ready to show ads: no internet connection available",
		"Unity Ads not ready to show ads: no ads are available",
		"Unity Ads not ready to show ads: zero ads available",
		"Unity Ads not ready to show ads: video not cached",
	};

	private static void logCanShow(int reason) {
		if(reason != prevCanShowLogMsg) {
			prevCanShowLogMsg = reason;
			UnityAdsDeviceLog.info(canShowLogMsgs[reason]);
		}
	}

	/* PUBLIC MULTIPLE REWARD ITEM SUPPORT */

	public static boolean hasMultipleRewardItems () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone != null && zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			return itemManager.itemCount() > 1;
		}
		return false;
	}

	public static ArrayList<String> getRewardItemKeys () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone != null && zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			ArrayList<UnityAdsRewardItem> rewardItems = itemManager.allItems();
			ArrayList<String> rewardItemKeys = new ArrayList<String>();
			for (UnityAdsRewardItem rewardItem : rewardItems) {
				rewardItemKeys.add(rewardItem.getKey());
			}

			return rewardItemKeys;
		}
		return null;
	}

	public static String getDefaultRewardItemKey () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone != null && zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			return itemManager.getDefaultItem().getKey();
		}
		return null;
	}

	public static String getCurrentRewardItemKey () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone != null && zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			return itemManager.getCurrentItem().getKey();
		}
		return null;
	}

	public static boolean setRewardItemKey (String rewardItemKey) {
		if (canShow()) {
			UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
			if(zone != null && zone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
				return itemManager.setCurrentItem(rewardItemKey);
			}
		}
		return false;
	}

	public static void setDefaultRewardItemAsRewardItem () {
		if (canShow()) {
			UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
			if(zone != null && zone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
				itemManager.setCurrentItem(itemManager.getDefaultItem().getKey());
			}
		}
	}

	public static Map<String, String> getRewardItemDetailsWithKey (String rewardItemKey) {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone != null && zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			UnityAdsRewardItem rewardItem = itemManager.getItem(rewardItemKey);
			if (rewardItem != null) {
				return rewardItem.getDetails();
			}
			else {
				UnityAdsDeviceLog.info("Could not fetch reward item: " + rewardItemKey);
			}
		}
		return null;
	}


	/* LISTENER METHODS */

	// IUnityAdsMainViewListener
	public void onMainViewAction (UnityAdsMainViewAction action) {
		switch (action) {
			case BackButtonPressed:
				if (_showingAds) {
					close();
				}
				break;
			case VideoStart:
				if (_adsListener != null)
					_adsListener.onVideoStarted();

				ArrayList<UnityAdsCampaign> viewableCampaigns = webdata.getViewableVideoPlanCampaigns();

				if(viewableCampaigns.size() > 1) {
					UnityAdsCampaign nextCampaign = viewableCampaigns.get(1);

					if(cachemanager.isCampaignCached(UnityAdsProperties.SELECTED_CAMPAIGN, true) && !cachemanager.isCampaignCached(nextCampaign, true) && nextCampaign.allowCacheVideo()) {
						cachemanager.cacheNextVideo(nextCampaign);
					}
				}

				cancelPauseScreenTimer();
				break;
			case VideoEnd:
				UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_COUNT++;
				if (_adsListener != null && UnityAdsProperties.SELECTED_CAMPAIGN != null && !UnityAdsProperties.SELECTED_CAMPAIGN.isViewed()) {
					UnityAdsDeviceLog.info("Unity Ads video completed");
					UnityAdsProperties.SELECTED_CAMPAIGN.setCampaignStatus(UnityAdsCampaignStatus.VIEWED);
					_adsListener.onVideoCompleted(getCurrentRewardItemKey(), false);
				}
				break;
			case VideoSkipped:
				UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_COUNT++;
				if (_adsListener != null && UnityAdsProperties.SELECTED_CAMPAIGN != null && !UnityAdsProperties.SELECTED_CAMPAIGN.isViewed()) {
					UnityAdsDeviceLog.info("Unity Ads video skipped");
					UnityAdsProperties.SELECTED_CAMPAIGN.setCampaignStatus(UnityAdsCampaignStatus.VIEWED);
					_adsListener.onVideoCompleted(getCurrentRewardItemKey(), true);
				}
				break;
			case RequestRetryVideoPlay:
				UnityAdsDeviceLog.debug("Retrying video play, because something went wrong.");
				_preventVideoDoubleStart = false;
				playVideo(300);
				break;
		}
	}

	// IUnityAdsCacheListener
	@Override
	public void onCampaignUpdateStarted () {
		UnityAdsDeviceLog.debug("Campaign updates started.");
	}

	@Override
	public void onCampaignReady (UnityAdsCampaignHandler campaignHandler) {
		if (campaignHandler == null || campaignHandler.getCampaign() == null) return;

		UnityAdsDeviceLog.debug(campaignHandler.getCampaign().toString());
	}

	@Override
	public void onAllCampaignsReady () {
		UnityAdsDeviceLog.entered();
	}

	// IUnityAdsWebDataListener
	@SuppressWarnings("deprecation")
	@Override
	public void onWebDataCompleted () {
		UnityAdsDeviceLog.entered();
		JSONObject jsonData = null;
		boolean dataFetchFailed = false;
		boolean sdkIsCurrent = true;

		if (webdata.getData() != null && webdata.getData().has(UnityAdsConstants.UNITY_ADS_JSON_DATA_ROOTKEY)) {
			try {
				jsonData = webdata.getData().getJSONObject(UnityAdsConstants.UNITY_ADS_JSON_DATA_ROOTKEY);
			}
			catch (Exception e) {
				dataFetchFailed = true;
			}

			if (!dataFetchFailed) {
				setupCampaignRefreshTimer();

				if (jsonData.has(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SDK_IS_CURRENT_KEY)) {
					try {
						sdkIsCurrent = jsonData.getBoolean(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SDK_IS_CURRENT_KEY);
					}
					catch (Exception e) {
						dataFetchFailed = true;
					}
				}
			}
		}

		if (!dataFetchFailed && !sdkIsCurrent && UnityAdsProperties.getCurrentActivity() != null && UnityAdsUtils.isDebuggable(UnityAdsProperties.getCurrentActivity())) {
			_alertDialog = new AlertDialog.Builder(UnityAdsProperties.getCurrentActivity()).create();
			_alertDialog.setTitle("Unity Ads");
			_alertDialog.setMessage("You are not running the latest version of Unity Ads android. Please update your version (this dialog won't appear in release builds).");
			_alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					_alertDialog.dismiss();
				}
			});

			_alertDialog.show();
		}

		setup();
	}

	@Override
	public void onWebDataFailed () {
		if (_adsListener != null && !_adsReadySent)
			_adsListener.onFetchFailed();
	}


	// IUnityAdsWebBrigeListener
	@Override
	public void onPlayVideo(JSONObject data) {
		UnityAdsDeviceLog.entered();
		if (data.has(UnityAdsConstants.UNITY_ADS_WEBVIEW_EVENTDATA_CAMPAIGNID_KEY)) {
			String campaignId = null;

			try {
				campaignId = data.getString(UnityAdsConstants.UNITY_ADS_WEBVIEW_EVENTDATA_CAMPAIGNID_KEY);
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Could not get campaignId");
			}

			if (campaignId != null) {
				if (webdata != null && webdata.getCampaignById(campaignId) != null) {
					UnityAdsProperties.SELECTED_CAMPAIGN = webdata.getCampaignById(campaignId);
				}

				if (UnityAdsProperties.SELECTED_CAMPAIGN != null &&
					UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId() != null &&
					UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId().equals(campaignId)) {

					Boolean rewatch = false;

					try {
						rewatch = data.getBoolean(UnityAdsConstants.UNITY_ADS_WEBVIEW_EVENTDATA_REWATCH_KEY);
					}
					catch (Exception e) {
					}

					UnityAdsDeviceLog.debug("Selected campaign=" + UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId() + " isViewed: " + UnityAdsProperties.SELECTED_CAMPAIGN.isViewed());
					if (UnityAdsProperties.SELECTED_CAMPAIGN != null && (rewatch || !UnityAdsProperties.SELECTED_CAMPAIGN.isViewed())) {
						if(rewatch) {
							_preventVideoDoubleStart = false;
						}

						playVideo();
					}
				}
			}
		}
	}

	@Override
	public void onPauseVideo(JSONObject data) {
	}

	@Override
	public void onCloseAdsView(JSONObject data) {
		hide();
	}

	@Override
	public void onWebAppLoadComplete (JSONObject data) {
		UnityAdsDeviceLog.entered();
	}

	@Override
	public void onWebAppInitComplete (JSONObject data) {
		UnityAdsDeviceLog.entered();
		_webAppLoaded = true;
		Boolean dataOk = true;

		if(hasViewableAds()) {
			JSONObject setViewData = new JSONObject();

			try {
				setViewData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY, UnityAdsConstants.UNITY_ADS_WEBVIEW_API_INITCOMPLETE);
			}
			catch (Exception e) {
				dataOk = false;
			}

			if (dataOk) {
				mainview.webview.setWebViewCurrentView(UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_START, setViewData);
				sendReadyEvent();
			}
		}
	}

	public void onOrientationRequest(JSONObject data) {
		UnityAdsProperties.CURRENT_ACTIVITY.get().setRequestedOrientation(data.optInt("orientation", -1));
	}

	public void onOpenPlayStore (JSONObject data) {
		UnityAdsDeviceLog.entered();

	    if (data != null) {

	    	UnityAdsDeviceLog.debug(data.toString());

	    	String playStoreId = null;
	    	String clickUrl = null;
	    	Boolean bypassAppSheet = false;

	    	if (data.has(UnityAdsConstants.UNITY_ADS_PLAYSTORE_ITUNESID_KEY)) {
	    		try {
		    		playStoreId = data.getString(UnityAdsConstants.UNITY_ADS_PLAYSTORE_ITUNESID_KEY);
	    		}
	    		catch (Exception e) {
	    			UnityAdsDeviceLog.error("Could not fetch playStoreId");
	    		}
	    	}

	    	if (data.has(UnityAdsConstants.UNITY_ADS_PLAYSTORE_CLICKURL_KEY)) {
	    		try {
	    			clickUrl = data.getString(UnityAdsConstants.UNITY_ADS_PLAYSTORE_CLICKURL_KEY);
	    		}
	    		catch (Exception e) {
	    			UnityAdsDeviceLog.error("Could not fetch clickUrl");
	    		}
	    	}

	    	if (data.has(UnityAdsConstants.UNITY_ADS_PLAYSTORE_BYPASSAPPSHEET_KEY)) {
	    		try {
	    			bypassAppSheet = data.getBoolean(UnityAdsConstants.UNITY_ADS_PLAYSTORE_BYPASSAPPSHEET_KEY);
	    		}
	    		catch (Exception e) {
	    			UnityAdsDeviceLog.error("Could not fetch bypassAppSheet");
	    		}
	    	}

	    	if (playStoreId != null && !bypassAppSheet) {
	    		openPlayStoreAsIntent(playStoreId);
	    	}
	    	else if (clickUrl != null ){
	    		openPlayStoreInBrowser(clickUrl);
	    	}
	    }
	}

	@Override
	public void onLaunchIntent(JSONObject data) {
		try {
			Intent intent = parseLaunchIntent(data);

			if(intent == null) {
				UnityAdsDeviceLog.error("No suitable intent to launch");
				UnityAdsDeviceLog.debug("Intent JSON: " + data.toString());
				return;
			}

			Activity currentActivity = UnityAdsProperties.getCurrentActivity();
			if(currentActivity == null) {
				UnityAdsDeviceLog.error("Unable to launch intent: current activity is null");
				return;
			}

			currentActivity.startActivity(intent);
		} catch(Exception e) {
			UnityAdsDeviceLog.error("Failed to launch intent: " + e.getMessage());
		}
	}

	/* PRIVATE METHODS */

	private static Intent parseLaunchIntent(JSONObject data) {
		try {
			if(data.has("packageName") && !data.has("className") && !data.has("action") && !data.has("mimeType")) {
				Activity currentActivity = UnityAdsProperties.getCurrentActivity();
				if(currentActivity == null) {
					UnityAdsDeviceLog.error("Unable to parse data to generate intent: current activity is null");
					return null;
				}

				PackageManager pm = currentActivity.getPackageManager();
				Intent intent = pm.getLaunchIntentForPackage(data.getString("packageName"));

				if(intent != null && data.has("flags")) {
					intent.addFlags(data.getInt("flags"));
				}

				return intent;
			}

			Intent intent = new Intent();

			if(data.has("className") && data.has("packageName")) {
				intent.setClassName(data.getString("packageName"), data.getString("className"));
			}

			if(data.has("action")) {
				intent.setAction(data.getString("action"));
			}

			if(data.has("uri")) {
				intent.setData(Uri.parse(data.getString("uri")));
			}

			if(data.has("mimeType")) {
				intent.setType(data.getString("mimeType"));
			}

			if(data.has("categories")) {
				JSONArray array = data.getJSONArray("categories");

				if(array.length() > 0) {
					for(int i = 0; i < array.length(); i++) {
						intent.addCategory(array.getString(i));
					}
				}
			}

			if(data.has("flags")) {
				intent.setFlags(data.getInt("flags"));
			}

			if(data.has("extras")) {
				JSONArray array = data.getJSONArray("extras");

				for(int i = 0; i < array.length(); i++) {
					JSONObject item = array.getJSONObject(i);

					String key = item.getString("key");
					Object value = item.get("value");

					if(value instanceof String) {
						intent.putExtra(key, (String)value);
					} else if(value instanceof Integer) {
						intent.putExtra(key, ((Integer)value).intValue());
					} else if(value instanceof Double) {
						intent.putExtra(key, ((Double)value).doubleValue());
					} else if(value instanceof Boolean) {
						intent.putExtra(key, ((Boolean)value).booleanValue());
					} else {
						UnityAdsDeviceLog.error("Unable to parse launch intent extra " + key);
					}
				}
			}

			return intent;
		} catch(JSONException e) {
			UnityAdsDeviceLog.error("Exception while parsing intent json: " + e.getMessage());
			return null;
		}
	}

	private static void openPlayStoreAsIntent (String playStoreId) {
		UnityAdsDeviceLog.debug("Opening playstore activity with storeId: " + playStoreId);

		if (UnityAdsProperties.getCurrentActivity() != null) {
			try {
				UnityAdsProperties.getCurrentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + playStoreId)));
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Couldn't start PlayStore intent!");
			}
		}
	}

	private static void openPlayStoreInBrowser (String url) {
		UnityAdsDeviceLog.debug("Opening playStore in browser: " + url);

		if (UnityAdsProperties.getCurrentActivity() != null) {
			try {
				UnityAdsProperties.getCurrentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Couldn't start browser intent!");
			}
		}
	}

	public static void init (final Activity activity, String gameId, IUnityAdsListener listener) {
		if (_instance != null || _initialized) return;

		if(gameId == null || gameId.length() == 0) {
			throw new IllegalArgumentException("gameId is empty");
		} else {
			try {
				int gameIdInteger = Integer.parseInt(gameId);
				if(gameIdInteger <= 0) {
					throw new IllegalArgumentException("gameId is invalid");
				}
			} catch(NumberFormatException e) {
				throw new IllegalArgumentException("gameId does not parse as an integer");
			}
		}

		if(UnityAdsProperties.UNITY_VERSION != null && UnityAdsProperties.UNITY_VERSION.length() > 0) {
			UnityAdsDeviceLog.info("Initializing Unity Ads version " + UnityAdsConstants.UNITY_ADS_VERSION + " (Unity + " + UnityAdsProperties.UNITY_VERSION + ") with gameId " + gameId);
		} else {
			UnityAdsDeviceLog.info("Initializing Unity Ads version " + UnityAdsConstants.UNITY_ADS_VERSION + " with gameId " + gameId);
		}

		try {
			Class<?> unityAdsWebBridge = Class.forName("com.unity3d.ads.android.webapp.UnityAdsWebBridge");
			@SuppressWarnings("unused")
			Method handleWebEvent = unityAdsWebBridge.getMethod("handleWebEvent", String.class, String.class);
			UnityAdsDeviceLog.debug("UnityAds ProGuard check OK");
		} catch(ClassNotFoundException e) {
			UnityAdsDeviceLog.error("UnityAds ProGuard check fail: com.unity3d.ads.android.webapp.UnityAdsWebBridge class not found, check ProGuard settings");
			return;
		} catch(NoSuchMethodException e) {
			UnityAdsDeviceLog.error("UnityAds ProGuard check fail: com.unity3d.ads.android.webapp.handleWebEvent method not found, check ProGuard settings");
			return;
		} catch(Exception e) {
			UnityAdsDeviceLog.debug("UnityAds ProGuard check: Unknown exception: " + e);
		}

		String pkgName = activity.getPackageName();
		PackageManager pm = activity.getPackageManager();

		if(pkgName != null && pm != null) {
			try {
				PackageInfo pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES);

				for(int i = 0; i < pkgInfo.activities.length; i++) {
					if(pkgInfo.activities[i].launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {
						_singleTaskApplication = true;
						UnityAdsDeviceLog.debug("Running in singleTask application mode");
					}
				}
			} catch (Exception e) {
				UnityAdsDeviceLog.debug("Error while checking singleTask activities");
			}
		}

		if (_instance == null) {
			_instance = new UnityAds();
		}

		setListener(listener);

		UnityAdsProperties.UNITY_ADS_GAME_ID = gameId;
		UnityAdsProperties.BASE_ACTIVITY = new WeakReference<Activity>(activity);
		UnityAdsProperties.CURRENT_ACTIVITY = new WeakReference<Activity>(activity);
		UnityAdsVideoPausedView.initScreenMetrics(activity);

		UnityAdsDeviceLog.debug("Is debuggable=" + UnityAdsUtils.isDebuggable(activity));

		cachemanager = new UnityAdsCacheManager(activity);
		cachemanager.setDownloadListener(_instance);
		webdata = new UnityAdsWebData();
		webdata.setWebDataListener(_instance);

		//_instanceInitialized = true;

		new Thread(new Runnable() {
			public void run() {
				UnityAdsAdvertisingId.init(activity);
				if (webdata.initCampaigns()) {
					_initialized = true;
				}
			}
		}).start();
	}

	private static void close () {
		cancelPauseScreenTimer();
		if(UnityAdsProperties.getCurrentActivity() != null && UnityAdsProperties.getCurrentActivity() instanceof UnityAdsFullscreenActivity) {
			UnityAdsCloseRunner closeRunner = new UnityAdsCloseRunner();
			Handler handler = new Handler(Looper.getMainLooper());
			handler.postDelayed(closeRunner, 1);
		}
		else {
			UnityAdsDeviceLog.debug("Did not close");
		}
	}

	private static void open (final String view) {
		Boolean dataOk = true;
		final JSONObject data = new JSONObject();

		try  {
			UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();

			data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY, UnityAdsConstants.UNITY_ADS_WEBVIEW_API_OPEN);
			data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ZONE_KEY, zone.getZoneId());

			if(zone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
				data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_REWARD_ITEM_KEY, itemManager.getCurrentItem().getKey());
			}
		}
		catch (Exception e) {
			dataOk = false;
		}

		UnityAdsDeviceLog.debug("DataOk: " + dataOk);

		if (dataOk && view != null) {
			UnityAdsDeviceLog.debug("Opening with view:" + view + " and data:" + data.toString());

			if (mainview != null) {
				new Thread(new Runnable() {
					public void run() {
						UnityAdsUtils.runOnUiThread(new Runnable() {
							public void run() {
								if(mainview != null) {
									mainview.openAds(view, data);

									UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
									if (currentZone.noOfferScreen()) {
										playVideo();
									}

									if (_adsListener != null)
										_adsListener.onShow();
								} else {
									UnityAdsDeviceLog.error("mainview null after open, closing");
									close();
								}
							}
						});

					}
				}).start();
			}
		}
	}

	private static void setup () {
		initCache();
		setupViews();
	}

	private static void initCache () {
		UnityAdsDeviceLog.entered();
		if (_initialized) {
			// Update cache WILL START DOWNLOADS if needed, after this method you can check getDownloadingCampaigns which ones started downloading.
			cachemanager.updateCache(webdata.getVideoPlanCampaigns());
		}
	}

	private static void sendReadyEvent () {
		if (!_adsReadySent && _adsListener != null) {
			UnityAdsUtils.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if(!_adsReadySent) {
						UnityAdsDeviceLog.debug("Unity Ads ready.");
						_adsListener.onFetchCompleted();
						_adsReadySent = true;
					}
				}
			});
		}
	}

	private static void setupViews () {
		_webAppLoaded = false;

		if(mainview != null) {
			UnityAdsDeviceLog.debug("Destroying views");
			mainview.webview.destroy();
			mainview = null;
		}

		if (UnityAdsProperties.getCurrentActivity() != null) {
			mainview = new UnityAdsMainView(UnityAdsProperties.getCurrentActivity(), _instance, _instance);
		}
	}

	private static void playVideo () {
		playVideo(0);
	}

	private static void playVideo (long delay) {
		if(_preventVideoDoubleStart) {
			UnityAdsDeviceLog.debug("Prevent double start of video playback");
			return;
		}
		_preventVideoDoubleStart = true;

		UnityAdsDeviceLog.debug("Running threaded");
		UnityAdsPlayVideoRunner playVideoRunner = new UnityAdsPlayVideoRunner();
		UnityAdsUtils.runOnUiThread(playVideoRunner, delay);
	}

	private static void startFullscreenActivity () {
		Intent newIntent = new Intent(UnityAdsProperties.getCurrentActivity(), com.unity3d.ads.android.view.UnityAdsFullscreenActivity.class);
		int flags = Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK;

		UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if (currentZone.openAnimated()) {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK;
		}

		newIntent.addFlags(flags);

		try {
			UnityAdsProperties.getBaseActivity().startActivity(newIntent);
		}
		catch (ActivityNotFoundException e) {
			UnityAdsDeviceLog.error("Could not find UnityAdsFullScreenActivity (failed Android manifest merging?): " + e.getStackTrace());
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Weird error: " + e.getStackTrace());
		}
	}

	public static void checkMainview() {
		if(_fixMainview) {
			_fixMainview = false;
			if(mainview != null) {
				mainview.fixActivityAttachment();
			}
		}
	}

	public static void handleFullscreenDestroy() {
		if(_singleTaskApplication) {
			hideOperations();
			return;
		}

		if(_showingAds) {
			_fixMainview = true;
		}
	}

	private static void cancelPauseScreenTimer () {
		if (_pauseScreenTimer != null) {
			_pauseScreenTimer.cancel();
		}

		if (_pauseTimer != null) {
			_pauseTimer.cancel();
			_pauseTimer.purge();
		}

		_pauseScreenTimer = null;
		_pauseTimer = null;
	}

	private static void createPauseScreenTimer () {
		_pauseScreenTimer = new TimerTask() {
			@Override
			public void run() {
				Activity currentActivity = UnityAdsProperties.getCurrentActivity();
				if(currentActivity != null) {
					PowerManager pm = (PowerManager)currentActivity.getBaseContext().getSystemService(Context.POWER_SERVICE);
					if (!pm.isScreenOn()) {
						if(mainview != null && mainview.webview != null && mainview.webview.isWebAppLoaded()) {
							mainview.webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_HIDESPINNER, new JSONObject());
							close();
						}

						cancelPauseScreenTimer();
					}
				}
			}
		};

		_pauseTimer = new Timer();
		_pauseTimer.scheduleAtFixedRate(_pauseScreenTimer, 0, 50);
	}

	// After ad unit closes, ad plan is refreshed if necessary
	// If ad plan is not refreshed and next video is not yet cached, start caching
	private static void refreshCampaignsOrCacheNextVideo() {
		boolean refresh = false;

		if(_refreshAfterShowAds) {
			_refreshAfterShowAds = false;
			UnityAdsDeviceLog.debug("Starting delayed ad plan refresh");
			refresh = true;
		} else if(_campaignRefreshTimerDeadline > 0 && SystemClock.elapsedRealtime() > _campaignRefreshTimerDeadline) {
			removeCampaignRefreshTimer();
			UnityAdsDeviceLog.debug("Refreshing ad plan from server due to timer deadline");
			refresh = true;
		} else if(UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_MAX > 0 && UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_COUNT >= UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_MAX) {
			UnityAdsDeviceLog.debug("Refreshing ad plan from server due to endscreen limit");
			refresh = true;
		} else if(webdata != null && webdata.getVideoPlanCampaigns() != null && webdata.getViewableVideoPlanCampaigns().size() == 0) {
			UnityAdsDeviceLog.debug("All available videos watched, refreshing ad plan from server");
			refresh = true;
		}

		if(refresh) {
			new Thread(new Runnable() {
				public void run() {
					if(webdata != null) {
						webdata.initCampaigns();
					}
				}
			}).start();

			return;
		}

		// Ad plan not refreshed, cache next video if necessary
		if(webdata == null) return;

		ArrayList<UnityAdsCampaign> viewableCampaigns = webdata.getViewableVideoPlanCampaigns();
		if(viewableCampaigns != null && viewableCampaigns.size() > 0) {
			UnityAdsCampaign nextCampaign = viewableCampaigns.get(0);

			if(!cachemanager.isCampaignCached(nextCampaign, false) && nextCampaign.allowCacheVideo()) {
				cachemanager.cacheNextVideo(nextCampaign);
			}
		}
	}

	private static void setupCampaignRefreshTimer() {
		removeCampaignRefreshTimer();

		if(UnityAdsProperties.CAMPAIGN_REFRESH_SECONDS > 0) {
			_campaignRefreshTimerTask = new TimerTask() {
				@Override
				public void run() {
					if(!_showingAds) {
						UnityAdsDeviceLog.debug("Refreshing ad plan to get new data");
						webdata.initCampaigns();
					} else {
						UnityAdsDeviceLog.debug("Refreshing ad plan after current ad");
						_refreshAfterShowAds = true;
					}
				}
			};

			_campaignRefreshTimerDeadline = SystemClock.elapsedRealtime() + UnityAdsProperties.CAMPAIGN_REFRESH_SECONDS * 1000;
			_campaignRefreshTimer = new Timer();
			_campaignRefreshTimer.schedule(_campaignRefreshTimerTask, UnityAdsProperties.CAMPAIGN_REFRESH_SECONDS * 1000);
		}
	}

	private static void removeCampaignRefreshTimer() {
		_campaignRefreshTimerDeadline = 0;

		if(_campaignRefreshTimer != null) {
			_campaignRefreshTimer.cancel();
		}
	}

	/* INTERNAL CLASSES */

	// FIX: Could these 2 classes be moved to MainView

	private static class UnityAdsCloseRunner implements Runnable {
		@Override
		public void run() {
			if (UnityAdsProperties.getCurrentActivity() != null && UnityAdsProperties.getCurrentActivity() instanceof UnityAdsFullscreenActivity) {
				final Activity currentActivity = UnityAdsProperties.getCurrentActivity();
				if (currentActivity != null && currentActivity instanceof UnityAdsFullscreenActivity && !currentActivity.isFinishing() && !UnityAdsProperties.isActivityDestroyed(currentActivity)) {
					currentActivity.finish();
					if (UnityAdsWebData.getZoneManager() != null) {
						UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
						if (!currentZone.openAnimated()) {
							currentActivity.overridePendingTransition(0, 0);
						}
					}
				}
			}

			hideOperations();
		}
	}

	private static void hideOperations() {
		if(_hidingHandled) {
			return;
		}

		int delay = 30;
		if(_singleTaskApplication) {
			delay = 250;
		}

		Handler handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if(_hidingHandled) {
					return;
				}

				_hidingHandled = true;

				final JSONObject data = new JSONObject();

				try  {
					data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY, UnityAdsConstants.UNITY_ADS_WEBVIEW_API_CLOSE);
				}
				catch (Exception e) {
					return;
				}

				if(mainview != null && mainview.webview != null) {
					mainview.webview.setWebViewCurrentView(UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_NONE, data);
				}

				if(mainview != null) {
					mainview.closeAds(data);
				}

				_showingAds = false;

				if (_adsListener != null)
					_adsListener.onHide();

				refreshCampaignsOrCacheNextVideo();
			}
		}, delay);
	}

	private static class UnityAdsPlayVideoRunner implements Runnable {

		@Override
		public void run() {
			UnityAdsDeviceLog.entered();
			if (UnityAdsProperties.SELECTED_CAMPAIGN != null) {
				UnityAdsDeviceLog.debug("Selected campaign found");
				JSONObject data = new JSONObject();

				try {
					data.put(UnityAdsConstants.UNITY_ADS_TEXTKEY_KEY, UnityAdsConstants.UNITY_ADS_TEXTKEY_BUFFERING);
				}
				catch (Exception e) {
					UnityAdsDeviceLog.error("Couldn't create data JSON");
					return;
				}

				mainview.webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_SHOWSPINNER, data);

				createPauseScreenTimer();

				String playUrl;
				if (!cachemanager.isCampaignCached(UnityAdsProperties.SELECTED_CAMPAIGN, true)) {
					playUrl = UnityAdsProperties.SELECTED_CAMPAIGN.getVideoStreamUrl();
					UnityAdsProperties.SELECTED_CAMPAIGN_CACHED = false;
				} else {
					playUrl = UnityAdsUtils.getCacheDirectory() + "/" + UnityAdsProperties.SELECTED_CAMPAIGN.getVideoFilename();
					UnityAdsProperties.SELECTED_CAMPAIGN_CACHED = true;
				}

				mainview.setViewState(UnityAdsMainViewState.VideoPlayer);
				UnityAdsDeviceLog.debug("Start videoplayback with: " + playUrl);
				mainview.videoplayerview.playVideo(playUrl);
			}
			else
				UnityAdsDeviceLog.error("Campaign is null");
		}
	}
}
