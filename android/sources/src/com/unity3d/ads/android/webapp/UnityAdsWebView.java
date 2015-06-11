package com.unity3d.ads.android.webapp;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.unity3d.ads.android.UnityAdsDeviceLog;
import com.unity3d.ads.android.UnityAdsUtils;
import com.unity3d.ads.android.data.UnityAdsDevice;
import com.unity3d.ads.android.properties.UnityAdsConstants;
import com.unity3d.ads.android.properties.UnityAdsProperties;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class UnityAdsWebView extends WebView {

	private String _url = null;
	private IUnityAdsWebViewListener _listener = null;
	private boolean _webAppLoaded = false;
	
	private UnityAdsWebBridge _webBridge = null;
	private String _currentWebView = UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_START;
	
	public UnityAdsWebView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
	}

	public UnityAdsWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public UnityAdsWebView(Context context) {
		super(context);
	}

	public UnityAdsWebView(Activity activity, IUnityAdsWebViewListener listener, UnityAdsWebBridge webBridge) {
		super(activity);
		UnityAdsDeviceLog.debug("Loading WebView from URL: " + UnityAdsProperties.WEBVIEW_BASE_URL);
		init(activity, UnityAdsProperties.WEBVIEW_BASE_URL, listener, webBridge);
	}

	public UnityAdsWebView(Activity activity, String url, IUnityAdsWebViewListener listener, UnityAdsWebBridge webBridge) {
		super(activity);
		init(activity, url, listener, webBridge);
	}
	
	public void clearWebView () {
		_webAppLoaded = false;
		_listener = null;
		setWebViewClient(null);
		setWebChromeClient(null);
	}
	
	public boolean isWebAppLoaded () {
		return _webAppLoaded;
	}
	
	public String getWebViewCurrentView () {
		return _currentWebView;
	}
	
	public void setWebViewCurrentView (String view) {
		setWebViewCurrentView(view, null);
	}
	
	public void setWebViewCurrentView (String view, JSONObject data) {		
		if (isWebAppLoaded()) {
			String dataString = "{}";
			
			if (data != null)
				dataString = data.toString();
			
			String javascriptString = String.format("%s%s(\"%s\", %s);", UnityAdsConstants.UNITY_ADS_WEBVIEW_JS_PREFIX, UnityAdsConstants.UNITY_ADS_WEBVIEW_JS_CHANGE_VIEW, view, dataString);
			_currentWebView = view;
			UnityAdsUtils.runOnUiThread(new UnityAdsJavascriptRunner(javascriptString, this));
			UnityAdsDeviceLog.debug("Send change view to WebApp: " + javascriptString);
			
			if (data != null && UnityAdsProperties.getBaseActivity() != null) {
				String action = "test";
				try {
					action = data.getString(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY);
				}
				catch (Exception e) {
				}
				
				UnityAdsDeviceLog.debug("dataHasApiActionKey=" + data.has(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY));
				UnityAdsDeviceLog.debug("actionEqualsWebViewApiOpen=" + action.equals(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_OPEN));
				UnityAdsDeviceLog.debug("isDebuggable=" + UnityAdsUtils.isDebuggable(UnityAdsProperties.getBaseActivity()));
				UnityAdsDeviceLog.debug("runWebViewTests=" + UnityAdsProperties.RUN_WEBVIEW_TESTS);
				UnityAdsDeviceLog.debug("testJavaScriptContents=" + UnityAdsProperties.TEST_JAVASCRIPT);
				
				if (data.has(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY) &&
					action != null &&
					action.equals(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_OPEN) &&
					UnityAdsUtils.isDebuggable(UnityAdsProperties.getBaseActivity()) &&
					UnityAdsProperties.RUN_WEBVIEW_TESTS &&
					UnityAdsProperties.TEST_JAVASCRIPT != null) {
					UnityAdsDeviceLog.debug("Running test-javascript: " + UnityAdsProperties.TEST_JAVASCRIPT);
					UnityAdsUtils.runOnUiThread(new UnityAdsJavascriptRunner(UnityAdsProperties.TEST_JAVASCRIPT, this));
					UnityAdsProperties.RUN_WEBVIEW_TESTS = false;
				}
			}
		}
	}
	
	public void sendNativeEventToWebApp (String eventType, JSONObject data) {
		if (isWebAppLoaded()) {
			String dataString = "{}";
			
			if (data != null)
				dataString = data.toString();

			String javascriptString = String.format("%s%s(\"%s\", %s);", UnityAdsConstants.UNITY_ADS_WEBVIEW_JS_PREFIX, UnityAdsConstants.UNITY_ADS_WEBVIEW_JS_HANDLE_NATIVE_EVENT, eventType, dataString);
			UnityAdsDeviceLog.debug("Send native event to WebApp: " + javascriptString);
			UnityAdsUtils.runOnUiThread(new UnityAdsJavascriptRunner(javascriptString, this));
		}
	}
	
	public void initWebApp (JSONObject data) {
		if (isWebAppLoaded()) {
			JSONObject initData = new JSONObject();

			try {				
				// Basic data
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_CAMPAIGNDATA_KEY, data);
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_PLATFORM_KEY, "android");

				String advertisingId = UnityAdsDevice.getAdvertisingTrackingId();
				if(advertisingId != null) {
					initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_TRACKINGENABLED_KEY, UnityAdsDevice.isLimitAdTrackingEnabled() ? 0 : 1);

					String advertisingIdMd5 = UnityAdsUtils.Md5(advertisingId).toLowerCase();
					initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_ADVERTISINGTRACKINGID_KEY, advertisingIdMd5);
					initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_RAWADVERTISINGTRACKINGID_KEY, advertisingId);
				} else {
					if (!UnityAdsConstants.UNITY_ADS_DEVICEID_UNKNOWN.equals(UnityAdsDevice.getAndroidSerial())) {
						initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SERIALID_KEY, UnityAdsDevice.getAndroidSerial());
					}

					initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_MACADDRESS_KEY, UnityAdsDevice.getMacAddress());
				}

				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SDKVERSION_KEY, UnityAdsConstants.UNITY_ADS_VERSION);
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_GAMEID_KEY, UnityAdsProperties.UNITY_ADS_GAME_ID);
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SCREENDENSITY_KEY, UnityAdsDevice.getScreenDensity());
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SCREENSIZE_KEY, UnityAdsDevice.getScreenSize());
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_ZONES_KEY, UnityAdsWebData.getZoneManager().getZonesJson());

        if(UnityAdsProperties.UNITY_VERSION != null && UnityAdsProperties.UNITY_VERSION.length() > 0) {
          initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_UNITYVERSION_KEY, UnityAdsProperties.UNITY_VERSION);
        }

				// Tracking data
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SOFTWAREVERSION_KEY, UnityAdsDevice.getSoftwareVersion());
				initData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_DEVICETYPE_KEY, UnityAdsDevice.getDeviceType());
			}
			catch (Exception e) {
				UnityAdsDeviceLog.debug("Error creating webview init params");
				return;
			}

			String initString = String.format("%s%s(%s);", UnityAdsConstants.UNITY_ADS_WEBVIEW_JS_PREFIX, UnityAdsConstants.UNITY_ADS_WEBVIEW_JS_INIT, initData.toString());
			UnityAdsDeviceLog.debug("Initializing WebView with JS call: " + initString);
			UnityAdsUtils.runOnUiThread(new UnityAdsJavascriptRunner(initString, this));
		}
	}


	/* INTERNAL METHODS */

	private void init (Activity activity, String url, IUnityAdsWebViewListener listener, UnityAdsWebBridge webBridge) {
		_listener = listener;
		_url = url;
		_webBridge = webBridge;
		setupUnityAdsView();
		loadUrl(_url);
		
		if (Build.VERSION.SDK_INT > 8) {
			setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
				    return true;
				}
			});
			setLongClickable(false);
		}
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("SetJavaScriptEnabled")
	private void setupUnityAdsView ()  {
		getSettings().setJavaScriptEnabled(true);
		
		if (_url != null && _url.indexOf("_raw.html") != -1) {
			getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
			UnityAdsDeviceLog.debug("LOAD_NO_CACHE");
		}
		else {
			if (Build.VERSION.SDK_INT < 17 ) {				
				getSettings().setCacheMode(WebSettings.LOAD_NORMAL);
			}
			else {
				getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
			}
		}
		
		String appCachePath = null;
		if (getContext() != null && getContext().getCacheDir() != null) 
			appCachePath = getContext().getCacheDir().toString();
		
		getSettings().setSupportZoom(false);
		getSettings().setBuiltInZoomControls(false);
		getSettings().setLightTouchEnabled(false);
		getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
		getSettings().setSupportMultipleWindows(false);
		getSettings().setAllowFileAccess(false);
		
		setHorizontalScrollBarEnabled(false);
		setVerticalScrollBarEnabled(false);		

		setClickable(true);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setInitialScale(0);

		setBackgroundColor(Color.BLACK);
		setBackgroundDrawable(null);
		setBackgroundResource(0);
		
		setWebViewClient(new UnityAdsViewClient());
		setWebChromeClient(new UnityAdsViewChromeClient());

		if (appCachePath != null) {
			boolean appCache = true;
			
			if (Build.VERSION.SDK_INT <= 7) {
				appCache = false;
			}
  
			getSettings().setAppCacheEnabled(appCache);
			getSettings().setDomStorageEnabled(true);
			getSettings().setAppCacheMaxSize(1024*1024*10);
			getSettings().setAppCachePath(appCachePath);
		}
		
		UnityAdsDeviceLog.debug("Adding javascript interface");
		addJavascriptInterface(_webBridge, "unityadsnativebridge");
	}
	
	public void setRenderMode (int mode) {
		// WebView background will go white in SDK >= 11 if you don't set webview's
		// layer-type to software.
		try
		{
			Method layertype = View.class.getMethod("setLayerType", Integer.TYPE, Paint.class);
			layertype.invoke(this, mode, null);
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Could not invoke setLayerType");
		}		
	}
	
	/* OVERRIDE METHODS */
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				UnityAdsDeviceLog.entered();
		    	if (_listener != null)
		    		_listener.onBackButtonClicked(this);
		    	return true;
		}
    	
    	return false;
    }
	
	
	/* SUBCLASSES */
	
	private class UnityAdsViewChromeClient extends WebChromeClient {
		public void onConsoleMessage(String message, int lineNumber, String sourceID) {
			String sourceFile = sourceID;
			File tmp = null;
			
			try {
				tmp = new File(sourceID);
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Could not handle sourceId: " + e.getMessage());
			}
			
			if (tmp != null && tmp.getName() != null)
				sourceFile = tmp.getName();
			
			UnityAdsDeviceLog.debug("JavaScript (sourceId=" + sourceFile + ", line=" + lineNumber + "): " + message);
		}
		
		public void onReachedMaxAppCacheSize(long spaceNeeded, long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
			quotaUpdater.updateQuota(spaceNeeded * 2);
		}
	}
	
	private class UnityAdsViewClient extends WebViewClient {
		@Override
		public void onPageFinished (WebView webview, String url) {
			super.onPageFinished(webview, url);
			UnityAdsDeviceLog.debug("Finished url: "  + url);
			if (_listener != null && !_webAppLoaded) {
				_webAppLoaded = true;
				_listener.onWebAppLoaded();
			}
		}
		
		@Override
		public boolean shouldOverrideUrlLoading (WebView view, String url) {
			UnityAdsDeviceLog.debug("Trying to load url: " + url);
			return false;
		}
		
		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {		
			UnityAdsDeviceLog.error(errorCode + " (" + failingUrl + ") " + description);
			super.onReceivedError(view, errorCode, description, failingUrl);
		}

		@Override
		public void onLoadResource(WebView view, String url) {
			super.onLoadResource(view, url);
		}	
	}

	
	/* PRIVATE CLASSES */
	
	private class UnityAdsJavascriptRunner implements Runnable {
		
		private String _jsString = null;
		private WebView _webView = null;
		
		public UnityAdsJavascriptRunner (String jsString, WebView webView) {
			_jsString = jsString;
			_webView = webView;
		}
		
		@Override
		public void run() {
			if (_jsString != null) {
				try {
					if(Build.VERSION.SDK_INT >= 19) {
						try
						{
							Method evaluateJavascript = WebView.class.getMethod("evaluateJavascript", String.class, ValueCallback.class);
							evaluateJavascript.invoke(_webView, _jsString, null);
						}
						catch (Exception e) {
							UnityAdsDeviceLog.error("Could not invoke evaluateJavascript");
						}
					} else {
						loadUrl(_jsString);
					}
				}
				catch (Exception e) {
					UnityAdsDeviceLog.error("Error while processing JavaScriptString!");
				}
			}
			else {
				UnityAdsDeviceLog.error("Could not process JavaScript, the string is NULL");
			}
		}		
	}
}
