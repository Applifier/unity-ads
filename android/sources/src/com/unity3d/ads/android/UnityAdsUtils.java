package com.unity3d.ads.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.unity3d.ads.android.campaign.UnityAdsCampaign;

import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;

public class UnityAdsUtils {
	public static ArrayList<UnityAdsCampaign> createCampaignsFromJson (JSONObject json) {
		if (json != null && json.has("campaigns")) {
			ArrayList<UnityAdsCampaign> campaignData = new ArrayList<UnityAdsCampaign>();
			JSONArray receivedCampaigns = null;
			JSONObject currentCampaign = null;
			
			try {
				receivedCampaigns = json.getJSONArray("campaigns");
			}
			catch (Exception e) {
				Log.d(UnityAdsProperties.LOG_NAME, "Malformed JSON");
			}
			
			for (int i = 0; i < receivedCampaigns.length(); i++) {
				try {
					currentCampaign = receivedCampaigns.getJSONObject(i);
					campaignData.add(new UnityAdsCampaign(currentCampaign));
				}
				catch (Exception e) {
					Log.d(UnityAdsProperties.LOG_NAME, "Malformed JSON");
				}
			}
			
			return campaignData;
		}
		
		return null;
	}
	
	public static boolean isUsingWifi () {
		ConnectivityManager mConnectivity = null;
		mConnectivity = (ConnectivityManager)UnityAdsProperties.CURRENT_ACTIVITY.getSystemService(Context.CONNECTIVITY_SERVICE);

		if (mConnectivity == null)
			return false;

		TelephonyManager mTelephony = (TelephonyManager)UnityAdsProperties.CURRENT_ACTIVITY.getSystemService(Context.TELEPHONY_SERVICE);
		// Skip if no connection, or background data disabled
		NetworkInfo info = mConnectivity.getActiveNetworkInfo();
		if (info == null || !mConnectivity.getBackgroundDataSetting() || mTelephony == null) {
		    return false;
		}

		int netType = info.getType();
		if (netType == ConnectivityManager.TYPE_WIFI) {
		    return info.isConnected();
		}
		else {
			return false;
		}
	}

	public static JSONObject getPlatformProperties () {
		JSONObject params = new JSONObject();
		String deviceType = "";

		if (UnityAdsProperties.CURRENT_ACTIVITY != null &&
			UnityAdsProperties.CURRENT_ACTIVITY.getResources() != null &&
			UnityAdsProperties.CURRENT_ACTIVITY.getResources().getConfiguration() != null) {
			deviceType = "" + UnityAdsProperties.CURRENT_ACTIVITY.getResources().getConfiguration().screenLayout;
		}
		
		try {
			params.put("deviceId", getDeviceId(UnityAdsProperties.CURRENT_ACTIVITY));
			params.put("softwareVersion", Build.VERSION.SDK_INT);
			params.put("hardwareVersion", Build.MANUFACTURER + " " + Build.MODEL);
			params.put("deviceType", deviceType);
			params.put("apiVersion", UnityAdsProperties.UNITY_ADS_API_VERSION);
			params.put("platform", "android");
			params.put("connectionType", isUsingWifi() ? "wifi" : "cellular");
		}
		catch (Exception e) {
			Log.d(UnityAdsProperties.LOG_NAME, "JSON Error");
		}
		
		return params;
	}
	
	
	public static String readFile (File fileToRead, boolean addLineBreaks) {
		String fileContent = "";
		BufferedReader br = null;
		
		if (fileToRead.exists() && fileToRead.canRead()) {
			try {
				br = new BufferedReader(new FileReader(fileToRead));
				String line = null;
				
				while ((line = br.readLine()) != null) {
					fileContent = fileContent.concat(line);
					if (addLineBreaks)
						fileContent = fileContent.concat("\n");
				}
			}
			catch (Exception e) {
				Log.d(UnityAdsProperties.LOG_NAME, "Problem reading file: " + e.getMessage());
				return null;
			}
			
			try {
				br.close();
			}
			catch (Exception e) {
				Log.d(UnityAdsProperties.LOG_NAME, "Problem closing reader: " + e.getMessage());
			}
						
			return fileContent;
		}
		else {
			Log.d(UnityAdsProperties.LOG_NAME, "File did not exist or couldn't be read");
		}
		
		return null;
	}
	
	public static boolean writeFile (File fileToWrite, String content) {
		FileOutputStream fos = null;
		
		try {
			fos = new FileOutputStream(fileToWrite);
			fos.write(content.getBytes());
			fos.flush();
			fos.close();
		}
		catch (Exception e) {
			Log.d(UnityAdsProperties.LOG_NAME, "Could not write file: " + e.getMessage());
			return false;
		}
		
		Log.d(UnityAdsProperties.LOG_NAME, "Wrote file: " + fileToWrite.getAbsolutePath());
		
		return true;
	}
	
	public static void removeFile (String fileName) {
		File removeFile = new File (fileName);
		File cachedVideoFile = new File (UnityAdsUtils.getCacheDirectory() + "/" + removeFile.getName());
		
		if (cachedVideoFile.exists()) {
			if (!cachedVideoFile.delete())
				Log.d(UnityAdsProperties.LOG_NAME, "Could not delete: " + cachedVideoFile.getAbsolutePath());
			else
				Log.d(UnityAdsProperties.LOG_NAME, "Deleted: " + cachedVideoFile.getAbsolutePath());
		}
		else {
			Log.d(UnityAdsProperties.LOG_NAME, "File: " + cachedVideoFile.getAbsolutePath() + " doesn't exist.");
		}
	}
		
	public static ArrayList<UnityAdsCampaign> substractFromCampaignList (ArrayList<UnityAdsCampaign> fromList, ArrayList<UnityAdsCampaign> substractionList) {
		if (fromList == null) return null;
		if (substractionList == null) return fromList;
		
		ArrayList<UnityAdsCampaign> pruneList = null;
		
		for (UnityAdsCampaign fromCampaign : fromList) {
			boolean match = false;
			
			for (UnityAdsCampaign substractionCampaign : substractionList) {
				if (fromCampaign.getCampaignId().equals(substractionCampaign.getCampaignId())) {
					match = true;
					break;
				}					
			}
			
			if (match)
				continue;
			
			if (pruneList == null)
				pruneList = new ArrayList<UnityAdsCampaign>();
			
			pruneList.add(fromCampaign);
		}
		
		return pruneList;
	}
	
	public static String getCacheDirectory () {
		return Environment.getExternalStorageDirectory().toString() + "/" + UnityAdsProperties.CACHE_DIR_NAME;
	}
	
	public static File createCacheDir () {
		File tdir = new File (getCacheDirectory());
		tdir.mkdirs();
		return tdir;
	}
	
	public static boolean isFileRequiredByCampaigns (String fileName, ArrayList<UnityAdsCampaign> campaigns) {
		if (fileName == null || campaigns == null) return false;
		
		File seekFile = new File(fileName);
		
		for (UnityAdsCampaign campaign : campaigns) {
			File matchFile = new File(campaign.getVideoUrl());
			if (seekFile.getName().equals(matchFile.getName()))
				return true;
		}
		
		return false;
	}

	public static boolean isFileInCache (String fileName) {
		File targetFile = new File (fileName);
		File testFile = new File(getCacheDirectory() + "/" + targetFile.getName());
		return testFile.exists();
	}
	
	public static String getDeviceId (Context context) {
		String prefix = "";
		String deviceId = null;
		//get android id
		
		if  (deviceId == null || deviceId.length() < 3) {
			//get telephony id
			prefix = "aTUDID";
			try {
				TelephonyManager tmanager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
				deviceId = tmanager.getDeviceId();
			}
			catch (Exception e) {
				//maybe no permissions
			}
		}
		
		if  (deviceId == null || deviceId.length() < 3) {
			//get device serial no using private api
			prefix = "aSNO";
			try {
		        Class<?> c = Class.forName("android.os.SystemProperties");
		        Method get = c.getMethod("get", String.class);
		        deviceId = (String) get.invoke(c, "ro.serialno");
		    } 
			catch (Exception e) {
		    }
		}

		if  (deviceId == null || deviceId.length() < 3) {
			deviceId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
			prefix = "aID";
		}
		
		if  (deviceId == null || deviceId.length() < 3) {
			//get mac address
			prefix = "aWMAC";
			try {
				WifiManager wm = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
				deviceId = wm.getConnectionInfo().getMacAddress();
			} catch (Exception e) {
				//maybe no permissons or wifi off
			}
		}
		
		if  (deviceId == null || deviceId.length() < 3) {
			prefix = "aUnknown";
			deviceId = Build.MANUFACTURER + "-" + Build.MODEL + "-"+Build.FINGERPRINT; 
		}

		//Log.d(_logName, "DeviceID : " + prefix + "_" + deviceId);
		return prefix + "_" + deviceId;
	}
}
