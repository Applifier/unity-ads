package com.unity3d.ads.android.webapp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;

import com.unity3d.ads.android.UnityAdsDeviceLog;
import com.unity3d.ads.android.UnityAdsUtils;
import com.unity3d.ads.android.campaign.UnityAdsCampaign;
import com.unity3d.ads.android.campaign.UnityAdsCampaign.UnityAdsCampaignStatus;
import com.unity3d.ads.android.data.UnityAdsDevice;
import com.unity3d.ads.android.item.UnityAdsRewardItemManager;
import com.unity3d.ads.android.properties.UnityAdsConstants;
import com.unity3d.ads.android.properties.UnityAdsProperties;
import com.unity3d.ads.android.zone.UnityAdsIncentivizedZone;
import com.unity3d.ads.android.zone.UnityAdsZone;
import com.unity3d.ads.android.zone.UnityAdsZoneManager;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class UnityAdsWebData {
	
	private JSONObject _campaignJson = null;
	private ArrayList<UnityAdsCampaign> _campaigns = null;
	private IUnityAdsWebDataListener _listener = null;
	private ArrayList<UnityAdsUrlLoader> _urlLoaders = null;
	private UnityAdsUrlLoader _currentLoader = null;
	private Object _urlLoaderLock = null;
	private static UnityAdsZoneManager _zoneManager = null;
	private int _totalUrlsSent = 0;
	private int _totalLoadersCreated = 0;
	private int _totalLoadersHaveRun = 0;

	private boolean _isLoading = false;
	private boolean _initInProgress = false;

	private static boolean whitelistRequested = false;
	private static boolean installedAppsSent = false;

	public static enum UnityAdsVideoPosition { Start, FirstQuartile, MidPoint, ThirdQuartile, End;
		@SuppressLint("DefaultLocale")
		@Override
		public String toString () {
			String output = null;
			switch (this) {
				case FirstQuartile:
					output = "first_quartile";
					break;
				case MidPoint:
					output = "mid_point";
					break;
				case ThirdQuartile:
					output = "third_quartile";
					break;
				case End:
					output = "video_end";
					break;
				case Start:
					output = "video_start";
					break;
				default:
					output = name().toString().toLowerCase();
					break;					
			}
			
			return output;
		}
	};
	
	private static enum UnityAdsRequestType { Analytics, VideoPlan, VideoViewed, Unsent, AppWhitelist, InstalledApps;
		@SuppressLint("DefaultLocale")
		@Override
		public String toString () {
			String output = name().toString().toLowerCase();
			return output;
		}
		
		@SuppressLint("DefaultLocale")
		public static UnityAdsRequestType getValueOf (String value) {
			if (VideoPlan.toString().equals(value.toLowerCase()))
				return VideoPlan;
			else if (VideoViewed.toString().equals(value.toLowerCase()))
				return VideoViewed;
			else if (Unsent.toString().equals(value.toLowerCase()))
				return Unsent;
			
			return null;
		}
	};
	
	public UnityAdsWebData () {
		_urlLoaderLock = new Object();
	}
	
	public void setWebDataListener (IUnityAdsWebDataListener listener) {
		_listener = listener;
	}
	
	public ArrayList<UnityAdsCampaign> getVideoPlanCampaigns () {
		return _campaigns;
	}
	
	public UnityAdsCampaign getCampaignById (String campaignId) {
		if (campaignId != null && _campaigns != null) {
			for (int i = 0; i < _campaigns.size(); i++) {
				if (_campaigns.get(i) != null && _campaigns.get(i).getCampaignId() != null && _campaigns.get(i).getCampaignId().equals(campaignId))
					return _campaigns.get(i);
			}
		}
		
		return null;
	}
	
	public ArrayList<UnityAdsCampaign> getViewableVideoPlanCampaigns () {
		ArrayList<UnityAdsCampaign> viewableCampaigns = null;
		UnityAdsCampaign currentCampaign = null; 
		
		if (_campaigns != null) {
			viewableCampaigns = new ArrayList<UnityAdsCampaign>();
			for (int i = 0; i < _campaigns.size(); i++) {
				currentCampaign = _campaigns.get(i);
				if (currentCampaign != null && !currentCampaign.getCampaignStatus().equals(UnityAdsCampaignStatus.VIEWED))
					viewableCampaigns.add(currentCampaign);
			}
		}
		
		return viewableCampaigns;
	}

	public boolean initInProgress() {
		return _initInProgress;
	}

	// TODO: This method always returns true regardless of success. Needs refactoring.
	public boolean initCampaigns () {
		if(_initInProgress) {
			return true;
		}

		if (UnityAdsProperties.getBaseActivity() != null && UnityAdsUtils.isDebuggable(UnityAdsProperties.getBaseActivity()) && UnityAdsProperties.TEST_DATA != null) {
			campaignDataReceived(UnityAdsProperties.TEST_DATA);
			return true;
		}

		_initInProgress = true;

		try {
			Activity currentActivity = UnityAdsProperties.getCurrentActivity();
			if(currentActivity == null) {
				UnityAdsDeviceLog.error("initCampaigns failed due to currentActivity null");
				UnityAdsUtils.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						campaignDataFailed();
					}
				});
				return true;
			}
			
			boolean isConnected = false;
			ConnectivityManager cm = (ConnectivityManager)currentActivity.getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);

			if(cm != null) {
				NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
				isConnected = activeNetwork != null && activeNetwork.isConnected();
			}

			if(!isConnected) {
				UnityAdsDeviceLog.error("Device offline, can't init campaigns");
				UnityAdsUtils.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						campaignDataFailed();
					}
				});
				return true;
			}

			InetAddress adServer = InetAddress.getByName("impact.applifier.com");

			UnityAdsDeviceLog.debug("Ad server resolves to " + adServer);
			if(adServer.isLoopbackAddress()) {
				UnityAdsDeviceLog.error("initCampaigns failed, ad server resolves to loopback address (due to ad blocker?)");
				UnityAdsUtils.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						campaignDataFailed();
					}
				});
				return true;
			}
		} catch(UnknownHostException e) {
			UnityAdsDeviceLog.error("initCampaigns failed due to DNS error, unable to resolve ad server address");
			UnityAdsUtils.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					campaignDataFailed();
				}
			});
			return true;
		} catch(Exception e) {
			UnityAdsDeviceLog.debug("Unknown exception during DNS test: " + e);
		}

		String url = UnityAdsProperties.getCampaignQueryUrl();
		UnityAdsDeviceLog.info("Requesting Unity Ads ad plan from " + url);
		String[] parts = url.split("\\?");
		
		UnityAdsUrlLoaderCreator ulc = new UnityAdsUrlLoaderCreator(parts[0], parts[1], UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_GET, UnityAdsRequestType.VideoPlan, 0);
		UnityAdsUtils.runOnUiThread(ulc);
		
		checkFailedUrls();			

		return true;
	}
	
	public boolean sendCampaignViewProgress (UnityAdsCampaign campaign, UnityAdsVideoPosition position) {
		boolean progressSent = false;
		if (campaign == null) return progressSent;

		UnityAdsDeviceLog.info("Unity Ads video position: " + position.toString() + ", gamer id: " + UnityAdsProperties.UNITY_ADS_GAMER_ID);

		if (position != null && UnityAdsProperties.UNITY_ADS_GAMER_ID != null) {			
			String viewUrl = String.format("%s%s", UnityAdsProperties.UNITY_ADS_BASE_URL, UnityAdsConstants.UNITY_ADS_ANALYTICS_TRACKING_PATH);
			viewUrl = String.format("%s%s/video/%s/%s", viewUrl, UnityAdsProperties.UNITY_ADS_GAMER_ID, position.toString(), campaign.getCampaignId());
			viewUrl = String.format("%s/%s", viewUrl, UnityAdsProperties.UNITY_ADS_GAME_ID);

			UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
			String queryParams = String.format("%s=%s", UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_ZONE_KEY, currentZone.getZoneId());

			try {
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_PLATFORM_KEY, "android");

				String advertisingId = UnityAdsDevice.getAdvertisingTrackingId();
				if(advertisingId != null) {
					queryParams = String.format("%s&%s=%d", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_TRACKINGENABLED_KEY, UnityAdsDevice.isLimitAdTrackingEnabled() ? 0 : 1);

					String advertisingIdMd5 = UnityAdsUtils.Md5(advertisingId).toLowerCase();
					queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_ADVERTISINGTRACKINGID_KEY, URLEncoder.encode(advertisingIdMd5, "UTF-8"));
					queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_RAWADVERTISINGTRACKINGID_KEY, URLEncoder.encode(advertisingId, "UTF-8"));					
				} else {
					if (!UnityAdsDevice.getAndroidId(false).equals(UnityAdsConstants.UNITY_ADS_DEVICEID_UNKNOWN)) {
						queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_ANDROIDID_KEY, URLEncoder.encode(UnityAdsDevice.getAndroidId(true), "UTF-8"));
						queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_RAWANDROIDID_KEY, URLEncoder.encode(UnityAdsDevice.getAndroidId(false), "UTF-8"));
					}
				}

				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_GAMEID_KEY, URLEncoder.encode(UnityAdsProperties.UNITY_ADS_GAME_ID, "UTF-8"));
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_SDKVERSION_KEY, URLEncoder.encode(UnityAdsConstants.UNITY_ADS_VERSION, "UTF-8"));
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_SOFTWAREVERSION_KEY, URLEncoder.encode(UnityAdsDevice.getSoftwareVersion(), "UTF-8"));
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_HARDWAREVERSION_KEY, URLEncoder.encode(UnityAdsDevice.getHardwareVersion(), "UTF-8"));
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_DEVICETYPE_KEY, UnityAdsDevice.getDeviceType());
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_CONNECTIONTYPE_KEY, URLEncoder.encode(UnityAdsDevice.getConnectionType(), "UTF-8"));

				if(!UnityAdsDevice.isUsingWifi()) {
					queryParams = String.format("%s&%s=%d", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_ANDROIDNETWORKTYPE_KEY, UnityAdsDevice.getNetworkType());
				}

				if(UnityAdsProperties.CACHING_SPEED > 0) {
					queryParams = String.format("%s&%s=%d", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_CACHINGSPEED_KEY, UnityAdsProperties.CACHING_SPEED);
				}

				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_SCREENSIZE_KEY, UnityAdsDevice.getScreenSize());
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_SCREENDENSITY_KEY, UnityAdsDevice.getScreenDensity());

				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_INIT_QUERYPARAM_CACHEDPLAYBACK_KEY, UnityAdsProperties.SELECTED_CAMPAIGN_CACHED ? "true" : "false");
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Problems creating campaigns query: " + e.getMessage() + e.getStackTrace().toString());
			}
			
			if(currentZone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)currentZone).itemManager();
			    queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_REWARDITEM_KEY, itemManager.getCurrentItem().getKey());
			}
			
			if (currentZone.getGamerSid() != null) {
				queryParams = String.format("%s&%s=%s", queryParams, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_GAMERSID_KEY, currentZone.getGamerSid());
			}
			
			UnityAdsUrlLoaderCreator ulc = new UnityAdsUrlLoaderCreator(viewUrl, queryParams, UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_POST, UnityAdsRequestType.VideoViewed, 0);
			UnityAdsUtils.runOnUiThread(ulc);
			
			progressSent = true;
		}
		
		return progressSent;
	}
	
	public void sendAnalyticsRequest (String eventType, UnityAdsCampaign campaign) {
		if (campaign != null) {
			String viewUrl = String.format("%s",  UnityAdsProperties.ANALYTICS_BASE_URL);
			String analyticsUrl = String.format("%s=%s", UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_GAMEID_KEY, UnityAdsProperties.UNITY_ADS_GAME_ID);
			analyticsUrl = String.format("%s&%s=%s", analyticsUrl, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_EVENTTYPE_KEY, eventType);
			analyticsUrl = String.format("%s&%s=%s", analyticsUrl, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_TRACKINGID_KEY, UnityAdsProperties.UNITY_ADS_GAMER_ID);
			analyticsUrl = String.format("%s&%s=%s", analyticsUrl, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_PROVIDERID_KEY, campaign.getCampaignId());
			
			UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
			analyticsUrl = String.format("%s&%s=%s", analyticsUrl, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_ZONE_KEY, currentZone.getZoneId());
			
			if(currentZone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)currentZone).itemManager();
				analyticsUrl = String.format("%s&%s=%s", analyticsUrl, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_REWARDITEM_KEY, itemManager.getCurrentItem().getKey());
			}		
			
			if (currentZone.getGamerSid() != null)
				analyticsUrl = String.format("%s&%s=%s", analyticsUrl, UnityAdsConstants.UNITY_ADS_ANALYTICS_QUERYPARAM_GAMERSID_KEY, currentZone.getGamerSid());
			
			UnityAdsUrlLoaderCreator ulc = new UnityAdsUrlLoaderCreator(viewUrl, analyticsUrl, UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_GET, UnityAdsRequestType.Analytics, 0);
			UnityAdsUtils.runOnUiThread(ulc);
		}
	}
	
	public void clearData () {
		if (_campaigns != null) {
			_campaigns.clear();
			_campaigns = null;
		}
		
		if (_zoneManager != null) {
			_zoneManager.clear();
			_zoneManager = null;
		}
		
		_campaignJson = null;
	}
	
	public void stopAllRequests () {
		synchronized(_urlLoaderLock) {
			if (_urlLoaders != null) {
				_urlLoaders.clear();
				_urlLoaders = null;
			}

			if (_currentLoader != null) {
				_currentLoader.cancel(true);
				_currentLoader = null;
			}
		}
	}
	
	public JSONObject getData () {
		return _campaignJson;
	}
	
	public String getVideoPlan () {
		if (_campaignJson != null)
			return _campaignJson.toString();
		
		return null;
	}	
	
	public static UnityAdsZoneManager getZoneManager() {
		return _zoneManager;
	}
	
	/* INTERNAL METHODS */
	
	private void addLoader (UnityAdsUrlLoader loader) {
		synchronized(_urlLoaderLock) {
			if (_urlLoaders == null)
				_urlLoaders = new ArrayList<UnityAdsWebData.UnityAdsUrlLoader>();
		
			_urlLoaders.add(loader);
		}
	}
	
	private void startNextLoader () {
		synchronized(_urlLoaderLock) {
			if (_urlLoaders != null && _urlLoaders.size() > 0 && !_isLoading) {
				UnityAdsDeviceLog.debug("Starting next URL loader");
				_isLoading = true;
				_currentLoader = (UnityAdsUrlLoader)_urlLoaders.remove(0).execute();
			}
		}
	}
	
	private void urlLoadCompleted (UnityAdsUrlLoader loader) {
		if (loader != null && loader.getRequestType() != null) {
			switch (loader.getRequestType()) {
				case VideoPlan:
					campaignDataReceived(loader.getData());
					break;
				case VideoViewed:
					break;
				case Unsent:
					break;
				case Analytics:
					break;
				case AppWhitelist:
					whitelistReceived(loader.getData());
				case InstalledApps:
					break;
			}
			
			loader.clear();
		}
		else {
			UnityAdsDeviceLog.error("Got broken urlLoader!");
		}
		
		_totalUrlsSent++;
		
		UnityAdsDeviceLog.debug("Total urls sent: " + _totalUrlsSent);
		
		_isLoading = false;
		startNextLoader();
	}

	private void urlLoadFailed (UnityAdsUrlLoader loader) {
		if (loader != null && loader.getRequestType() != null) {
			switch (loader.getRequestType()) {
				case Analytics:
				case VideoViewed:
				case Unsent:
					writeFailedUrl(loader);
					break;
				case VideoPlan:
					campaignDataFailed();
					break;
				case AppWhitelist:
					break;
				case InstalledApps:
					// never retry sending installed apps
					break;
			}

			loader.clear();
		}
		else {
			UnityAdsDeviceLog.error("Got broken urlLoader!");
		}

		_isLoading = false;
		startNextLoader();
	}

	private void checkFailedUrls () {
		File pendingRequestFile = new File(UnityAdsUtils.getCacheDirectory() + "/" + UnityAdsConstants.PENDING_REQUESTS_FILENAME);

		if (pendingRequestFile.exists()) {
			String contents;

			synchronized(_urlLoaderLock) {
				contents = UnityAdsUtils.readFile(pendingRequestFile, true);
				UnityAdsUtils.removeFile(pendingRequestFile.toString());
			}

			JSONObject pendingRequestsJson = null;
			JSONArray pendingRequestsArray = null;
			//UnityAdsUrlLoader loader = null;

			try {
				pendingRequestsJson = new JSONObject(contents);
				pendingRequestsArray = pendingRequestsJson.getJSONArray("data");
				
				if (pendingRequestsArray != null && pendingRequestsArray.length() > 0) {
					for (int i = 0; i < pendingRequestsArray.length(); i++) {
						JSONObject failedUrl = pendingRequestsArray.getJSONObject(i);
						
						UnityAdsUrlLoaderCreator ulc = new UnityAdsUrlLoaderCreator(
								failedUrl.getString(UnityAdsConstants.UNITY_ADS_FAILED_URL_URL_KEY), 
								failedUrl.getString(UnityAdsConstants.UNITY_ADS_FAILED_URL_BODY_KEY), 
								failedUrl.getString(UnityAdsConstants.UNITY_ADS_FAILED_URL_METHODTYPE_KEY), 
								UnityAdsRequestType.getValueOf(failedUrl.getString(UnityAdsConstants.UNITY_ADS_FAILED_URL_REQUESTTYPE_KEY)), 
								failedUrl.getInt(UnityAdsConstants.UNITY_ADS_FAILED_URL_RETRIES_KEY) + 1);
						
						UnityAdsUtils.runOnUiThread(ulc);
					}
				}
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Problems while sending some of the failed urls.");
			}
		}
		
		startNextLoader();
	}
	
	private void writeFailedUrl (UnityAdsUrlLoader loader) {
		if(loader == null) return;

		synchronized(_urlLoaderLock) {
			try {
				File pendingRequestFile = new File(UnityAdsUtils.getCacheDirectory() + "/" + UnityAdsConstants.PENDING_REQUESTS_FILENAME);
				
				JSONObject pendingRequestsJson = null;
				JSONArray pendingRequestsArray = null;
				
				if(pendingRequestFile.exists()) {
					String contents = UnityAdsUtils.readFile(pendingRequestFile, true);

					try {
						pendingRequestsJson = new JSONObject(contents);
						UnityAdsDeviceLog.debug("JNIDEBUG read json: " + pendingRequestsJson.toString());
						pendingRequestsArray = pendingRequestsJson.getJSONArray("data");
						UnityAdsDeviceLog.debug("JNIDEBUG read array: " + pendingRequestsArray.toString());
					} catch(JSONException e) {
						pendingRequestsJson = null;
						pendingRequestsArray = null;
					}
				}

				if(pendingRequestsArray == null) {
					pendingRequestsArray = new JSONArray();
				}

				if(pendingRequestsJson == null) {
					pendingRequestsJson = new JSONObject();
				}

				JSONObject failedUrl = new JSONObject();
				failedUrl.put(UnityAdsConstants.UNITY_ADS_FAILED_URL_URL_KEY, loader.getBaseUrl());
				failedUrl.put(UnityAdsConstants.UNITY_ADS_FAILED_URL_REQUESTTYPE_KEY, loader.getRequestType());
				failedUrl.put(UnityAdsConstants.UNITY_ADS_FAILED_URL_METHODTYPE_KEY, loader.getHTTPMethod());
				failedUrl.put(UnityAdsConstants.UNITY_ADS_FAILED_URL_BODY_KEY, loader.getQueryParams());				
				failedUrl.put(UnityAdsConstants.UNITY_ADS_FAILED_URL_RETRIES_KEY, loader.getRetries());

				pendingRequestsArray.put(failedUrl);
				pendingRequestsJson.put("data", pendingRequestsArray);

				if(UnityAdsUtils.canUseExternalStorage()) {
					UnityAdsUtils.writeFile(pendingRequestFile, pendingRequestsJson.toString());
				}
			} catch(Exception e) {
				UnityAdsDeviceLog.debug("Exception when writing failed url: " + e.getMessage());
			}
		}
	}

	private void campaignDataReceived(String json) {
		Boolean validData = true;

		_initInProgress = false;

		try {
			UnityAdsDeviceLog.debug("Ad plan: " + json);

			_campaignJson = new JSONObject(json);
			JSONObject data = null;

			if (_campaignJson.has(UnityAdsConstants.UNITY_ADS_JSON_DATA_ROOTKEY)) {
				try {
					data = _campaignJson.getJSONObject(UnityAdsConstants.UNITY_ADS_JSON_DATA_ROOTKEY);
				}
				catch(Exception e) {
					UnityAdsDeviceLog.error("Malformed data JSON");
				}

				if(!data.has(UnityAdsConstants.UNITY_ADS_WEBVIEW_URL_KEY)) validData = false;
				if(!data.has(UnityAdsConstants.UNITY_ADS_ANALYTICS_URL_KEY)) validData = false;
				if(!data.has(UnityAdsConstants.UNITY_ADS_URL_KEY)) validData = false;
				if(!data.has(UnityAdsConstants.UNITY_ADS_GAMER_ID_KEY)) validData = false;
				if(!data.has(UnityAdsConstants.UNITY_ADS_CAMPAIGNS_KEY)) validData = false;
				if(!data.has(UnityAdsConstants.UNITY_ADS_ZONES_KEY)) validData = false;

				// Parse campaigns
				if(validData) {
					ArrayList<UnityAdsCampaign> tmpCampaigns = null;

					JSONArray campaigns = data.getJSONArray(UnityAdsConstants.UNITY_ADS_CAMPAIGNS_KEY);
					if (campaigns != null) {
						tmpCampaigns = deserializeCampaigns(campaigns);
					}

					if(data.has(UnityAdsConstants.UNITY_ADS_CAMPAIGN_APPFILTERING_KEY)) {
						String appFiltering = data.getString(UnityAdsConstants.UNITY_ADS_CAMPAIGN_APPFILTERING_KEY);

						if(appFiltering != null && (appFiltering.equals("simple") || appFiltering.equals("advanced"))) {
							if(appFiltering.equals("advanced")) {
								if(data.has(UnityAdsConstants.UNITY_ADS_CAMPAIGN_INSTALLED_APPS_URL) && data.has(UnityAdsConstants.UNITY_ADS_CAMPAIGN_APP_WHITELIST_URL)) {
									UnityAdsProperties.INSTALLED_APPS_URL = data.getString(UnityAdsConstants.UNITY_ADS_CAMPAIGN_INSTALLED_APPS_URL);
									String whitelistUrl = data.getString(UnityAdsConstants.UNITY_ADS_CAMPAIGN_APP_WHITELIST_URL);

									requestAppWhitelist(whitelistUrl);
								}
							}

							if(tmpCampaigns != null && tmpCampaigns.size() > 0) {
								ArrayList<UnityAdsCampaign> filteredCampaigns = filterCampaigns(tmpCampaigns);

								if(filteredCampaigns != null && filteredCampaigns.size() == 0) {
									initCampaigns();
									return;
								}

								tmpCampaigns = filteredCampaigns;
							}
						}
					}

					_campaigns = tmpCampaigns;
				}

				// Fall back, if campaigns were not found just set it to size 0
				if(_campaigns == null)
					_campaigns = new ArrayList<UnityAdsCampaign>();

				UnityAdsDeviceLog.debug("Parsed total of " + _campaigns.size() + " campaigns");

				// Parse basic properties
				UnityAdsProperties.WEBVIEW_BASE_URL = data.getString(UnityAdsConstants.UNITY_ADS_WEBVIEW_URL_KEY);
				UnityAdsProperties.ANALYTICS_BASE_URL = data.getString(UnityAdsConstants.UNITY_ADS_ANALYTICS_URL_KEY);
				UnityAdsProperties.UNITY_ADS_BASE_URL = data.getString(UnityAdsConstants.UNITY_ADS_URL_KEY);
				UnityAdsProperties.UNITY_ADS_GAMER_ID = data.getString(UnityAdsConstants.UNITY_ADS_GAMER_ID_KEY);

				// Refresh campaigns after "n" endscreens
				if(data.has(UnityAdsConstants.UNITY_ADS_CAMPAIGN_REFRESH_VIEWS_KEY)) {
					UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_COUNT = 0;
					UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_MAX = data.getInt(UnityAdsConstants.UNITY_ADS_CAMPAIGN_REFRESH_VIEWS_KEY);
				}

				// Refresh campaigns after "n" seconds
				if(data.has(UnityAdsConstants.UNITY_ADS_CAMPAIGN_REFRESH_SECONDS_KEY)) {
					UnityAdsProperties.CAMPAIGN_REFRESH_SECONDS = data.getInt(UnityAdsConstants.UNITY_ADS_CAMPAIGN_REFRESH_SECONDS_KEY);
				}

				// Zone parsing
				if(validData) {
					if(_zoneManager != null) {
						_zoneManager.clear();
						_zoneManager = null;
					}
					_zoneManager = new UnityAdsZoneManager(data.getJSONArray(UnityAdsConstants.UNITY_ADS_ZONES_KEY));
				}
			}
			else {
				campaignDataFailed();
				return;
			}
		}
		catch(Exception e) {
			UnityAdsDeviceLog.error("Malformed JSON: " + e.getMessage());

			if (e.getStackTrace() != null) {
				for (StackTraceElement element : e.getStackTrace()) {
					UnityAdsDeviceLog.error("Malformed JSON: " + element.toString());
				}
			}

			campaignDataFailed();
			return;
		}

		if(_listener != null && validData && _campaigns != null && _campaigns.size() > 0) {
			UnityAdsDeviceLog.info("Unity Ads initialized with " + _campaigns.size() + " campaigns and " + (_zoneManager != null ? _zoneManager.zoneCount() : 0) + " zones");
			_listener.onWebDataCompleted();
			return;
		}
		else {
			campaignDataFailed();
			return;
		}
	}

	private void campaignDataFailed () {
		if (_listener != null)
			_listener.onWebDataFailed();		
	}

	private ArrayList<UnityAdsCampaign> deserializeCampaigns(JSONArray campaignsArray) {
		if (campaignsArray != null && campaignsArray.length() > 0) {			
			UnityAdsCampaign campaign = null;
			ArrayList<UnityAdsCampaign> retList = new ArrayList<UnityAdsCampaign>();

			for (int i = 0; i < campaignsArray.length(); i++) {
				try {
					JSONObject jsonCampaign = campaignsArray.getJSONObject(i);
					campaign = new UnityAdsCampaign(jsonCampaign);

					if (campaign.hasValidData()) {
						UnityAdsDeviceLog.debug("Adding campaign to cache");
						retList.add(campaign);
					}
				}
				catch (Exception e) {
					UnityAdsDeviceLog.error("Problem with the campaign, skipping.");
				}
			}

			return retList;
		}

		return null;
	}

	private ArrayList<UnityAdsCampaign> filterCampaigns(ArrayList<UnityAdsCampaign> campaigns) {
		if(campaigns == null || campaigns.size() == 0) return null;

		Activity activity = UnityAdsProperties.getCurrentActivity();

		if(activity == null) return campaigns;

		PackageManager pm = activity.getPackageManager();

		ArrayList<UnityAdsCampaign> newCampaigns = new ArrayList<UnityAdsCampaign>();
		ArrayList<String> installedCampaigns = null;

		for(UnityAdsCampaign campaign : campaigns) {
			String packageName = campaign.getStoreId();

			// Sometimes getStoreId returns stuff like com.company.game&hl=en so strip ampersand(0x26) and everything after that
			if(packageName.indexOf(0x26) != -1) {
				packageName = packageName.substring(0, packageName.indexOf(0x26));
			}

			if(packageName != null) {
				boolean installed = false;

				// Check if packageName is installed
				try {
					PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);
					if(pkgInfo != null && packageName.equals(pkgInfo.packageName)) {
						installed = true;
					}
				} catch(NameNotFoundException e) {
					installed = false;
				}

				// Add all installed games to list for next ad request
				if(installed) {
					if(installedCampaigns == null) {
						installedCampaigns = new ArrayList<String>();
					}

					installedCampaigns.add(campaign.getGameId());
				}

				String filterMode = campaign.getFilterMode();

				if(filterMode != null && filterMode.equals("whitelist")) {
					// In whitelist mode, show ads for game only when the game is already installed
					if(installed) {
						newCampaigns.add(campaign);
					} else {
						UnityAdsDeviceLog.debug("Filtered game id " + campaign.getGameId() + " from ad plan (not installed)");
					}
				} else {
					// In blacklist mode (default), show ads for game only when the game is not already installed
					if(installed) {
						UnityAdsDeviceLog.debug("Filtered game id " + campaign.getGameId() + " from ad plan (already installed)");
					} else {
						newCampaigns.add(campaign);
					}
				}
			} else {
				// If package name is not available for any reason, do no filtering
				newCampaigns.add(campaign);
			}
		}

		if(installedCampaigns != null) {
			UnityAdsProperties.APPFILTER_LIST = TextUtils.join(",", installedCampaigns);
		}

		return newCampaigns;
	}

	private void requestAppWhitelist(String url) {
		if(whitelistRequested) return;

		whitelistRequested = true;

		UnityAdsUrlLoaderCreator ulc = new UnityAdsUrlLoaderCreator(url, null, UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_GET, UnityAdsRequestType.AppWhitelist, 0);
		UnityAdsUtils.runOnUiThread(ulc);
	}

	private void whitelistReceived(String json) {
		UnityAdsDeviceLog.debug("Received whitelist");

		try {
			JSONObject appWhitelist = new JSONObject(json);
			HashMap<String,String> parsedWhitelist = new HashMap<String,String>();

			JSONArray whitelistArray = appWhitelist.getJSONArray("whitelist");

			for(int i = 0; i < whitelistArray.length(); i++) {
				try {
					JSONObject appEntry = whitelistArray.getJSONObject(i);

					if(appEntry.has("game") && appEntry.has("id")) {
						parsedWhitelist.put(appEntry.getString("game").toUpperCase(), appEntry.getString("id"));
					}
				} catch(JSONException e) {
					// Continue to next array item if there were errors during parsing
				}
			}

			sendInstalledApps(UnityAdsProperties.INSTALLED_APPS_URL, parsedWhitelist);
		} catch(Exception e) {
			UnityAdsDeviceLog.debug("Failed to parse app whitelist " + e);
		}
	}

	private void sendInstalledApps(String url, Map<String,String> whitelist) {
		if(installedAppsSent) return;

		installedAppsSent = true;

		String appsJson = UnityAdsDevice.getPackageDataJson(whitelist);

		if(appsJson != null) {
			UnityAdsUrlLoaderCreator ulc = new UnityAdsUrlLoaderCreator(url, UnityAdsProperties.getCampaignQueryArguments(), UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_POST, UnityAdsRequestType.InstalledApps, 0);
			ulc.setPostBody(appsJson);
			UnityAdsUtils.runOnUiThread(ulc);
		} else {
			UnityAdsDeviceLog.debug("Nothing to send for installed applications");
		}
	}

	/* INTERNAL CLASSES */
	
	private class UnityAdsUrlLoaderCreator implements Runnable {
		private String _url = null;
		private String _queryParams = null;
		private String _requestMethod = null;
		private UnityAdsRequestType _requestType = null;
		private int _retries = 0;
		private String _postBody = null;

		public UnityAdsUrlLoaderCreator (String urlPart1, String urlPart2, String requestMethod, UnityAdsRequestType requestType, int retries) {
			_url = urlPart1;
			_queryParams = urlPart2;
			_requestMethod = requestMethod;
			_requestType = requestType;
			_retries = retries;
		}

		public void setPostBody(String body) {
			_postBody = body;
		}

		public void run () {
			UnityAdsUrlLoader loader = new UnityAdsUrlLoader(_url, _queryParams, _requestMethod, _requestType, _retries);
			UnityAdsDeviceLog.debug("URL: " + loader.getUrl());

			if(_postBody != null) {
				loader.setPostBody(_postBody);
			}

			if (_retries <= UnityAdsProperties.MAX_NUMBER_OF_ANALYTICS_RETRIES)
				addLoader(loader);

			startNextLoader();
		}
	}

	private class UnityAdsCancelUrlLoaderRunner implements Runnable {
		private UnityAdsUrlLoader _loader = null;
		public UnityAdsCancelUrlLoaderRunner (UnityAdsUrlLoader loader) {
			_loader = loader;
		}
		public void run () {
			try {
				_loader.cancel(true);
				//_loader.clear();
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Cancelling urlLoader got exception: " + e.getMessage());
			}
		}
	}
	
	private class UnityAdsUrlLoader extends AsyncTask<String, Integer, String> {
		private URL _url = null;
		private HttpURLConnection _connection = null;
		private int _downloadLength = 0;
		private InputStream _input = null;
		private BufferedInputStream _binput = null;
		private String _urlData = "";
		private UnityAdsRequestType _requestType = null;
		private String _finalUrl = null;
		private int _retries = 0;
		private String _httpMethod = UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_GET;
		private String _queryParams = null;
		private String _baseUrl = null;
		private Boolean _done = false;
		private String _postBody = null;

		public UnityAdsUrlLoader (String url, String queryParams, String httpMethod, UnityAdsRequestType requestType, int existingRetries) {
			super();
			try {
				_finalUrl = url;
				_baseUrl = url;
				
				if (httpMethod.equals(UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_GET) && queryParams != null && queryParams.length() > 2) {
					_finalUrl += "?" + queryParams;
				}
				
				_url = new URL(_finalUrl);
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Problems with url! Error-message: " + e.getMessage());
			}
			
			_queryParams = queryParams;
			_httpMethod = httpMethod;
			_totalLoadersCreated++;
			UnityAdsDeviceLog.debug("Total urlLoaders created: " + _totalLoadersCreated);
			_requestType = requestType;
			_retries = existingRetries;
		}
		
		public int getRetries () {
			return _retries;
		}
		
		public String getUrl () {
			return _url.toString();
		}
		
		public String getBaseUrl () {
			return _baseUrl;
		}
		
		public String getData () {
			return _urlData;
		}
		
		public String getQueryParams () {
			return _queryParams;
		}
		
		public String getHTTPMethod () {
			return _httpMethod;
		}
		
		public UnityAdsRequestType getRequestType () {
			return _requestType;
		}

		public void setPostBody(String body) {
			if(_queryParams != null && _queryParams.length() > 2) {
				_finalUrl = _baseUrl + "?" + _queryParams;
				try {
					_url = new URL(_finalUrl);
				} catch(MalformedURLException e) {
					UnityAdsDeviceLog.error("Error when creating adding query parameters to URL " + e);
				}
			}

			_postBody = body;
		}

		public void clear () {
			_url = null;
			_downloadLength = 0;
			_urlData = "";
			_requestType = null;
			_finalUrl = null;
			_retries = 0;
			_httpMethod = null;
			_queryParams = null;
			_baseUrl = null;
			_postBody = null;
		}
		
		private void cancelInMainThread () {
			UnityAdsUtils.runOnUiThread(new UnityAdsCancelUrlLoaderRunner(this));
		}
		
		@Override
		protected String doInBackground(String... params) {
			try {
				if (_url.toString().startsWith("https://")) {
					_connection = (HttpsURLConnection)_url.openConnection();
				}
				else {
					_connection = (HttpURLConnection)_url.openConnection();
				}

				_connection.setConnectTimeout(20000);
				_connection.setReadTimeout(30000);
				_connection.setRequestMethod(_httpMethod);
				if(_postBody == null) {
					_connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
				} else {
					_connection.setRequestProperty("Content-type", "application/json");
				}
				_connection.setDoInput(true);
				
				if (_httpMethod.equals(UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_POST))
					_connection.setDoOutput(true);
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Problems opening connection: " + e.getMessage());
				cancelInMainThread();
				return null;
			}
			
			if (_connection != null) {				
				if (_httpMethod.equals(UnityAdsConstants.UNITY_ADS_REQUEST_METHOD_POST)) {
					try {
						PrintWriter pout = new PrintWriter(new OutputStreamWriter(_connection.getOutputStream(), "UTF-8"), true);
						if(_postBody == null) {
							pout.print(_queryParams);
						} else {
							pout.print(_postBody);
						}
						pout.flush();
					}
					catch (Exception e) {
						UnityAdsDeviceLog.error("Problems writing post-data: " + e.getMessage() + ", " + e.getStackTrace());
						cancelInMainThread();
						return null;
					}
				}
				
				try {
					UnityAdsDeviceLog.debug("Connection response: " + _connection.getResponseCode() + ", " + _connection.getResponseMessage() + ", " + _connection.getURL().toString() + " : " + _queryParams);
					_input = _connection.getInputStream();
					_binput = new BufferedInputStream(_input);
				}
				catch (Exception e) {
					UnityAdsDeviceLog.error("Problems opening stream: " + e.getMessage());
					cancelInMainThread();
					return null;
				}
				
				long total = 0;
				_downloadLength = _connection.getContentLength();
				
				try {
					_totalLoadersHaveRun++;
					UnityAdsDeviceLog.debug("Total urlLoaders that have started running: " + _totalLoadersHaveRun);
					UnityAdsDeviceLog.debug("Reading data from: " + _url.toString() + " Content-length: " + _downloadLength);
					
					ByteArrayBuffer baf = new ByteArrayBuffer(1024 * 20);
					int current = 0;
					
					while ((current = _binput.read()) != -1) {
						total++;
						baf.append((byte)current);
						
						if (isCancelled())
							return null;
					}
					
					_urlData = new String(baf.toByteArray());
					UnityAdsDeviceLog.debug("Read total of: " + total);
				}
				catch (Exception e) {
					UnityAdsDeviceLog.error("Problems loading url! Error-message: " + e.getMessage());
					cancelInMainThread();
					return null;
				}
			}
			
			return null;
		}

		@Override
		protected void onCancelled() {
			_done = true;
			closeAndFlushConnection();
			urlLoadFailed(this);
		}

		@Override
		protected void onPostExecute(String result) {
			if (!isCancelled() && !_done) {
				_done = true;
				closeAndFlushConnection();
				urlLoadCompleted(this);
 			}
			
			super.onPostExecute(result);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
		}
		
		private void closeAndFlushConnection () {
			try {
				if(_input != null) {
					_input.close();
					_input = null;
				}

				if(_binput != null) {
					_binput.close();
					_binput = null;
				}
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Problems closing streams: " + e.getMessage());
			}	
		}
	}
}
