package com.unity3d.ads.android.data;

import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;

import com.unity3d.ads.android.UnityAdsDeviceLog;
import com.unity3d.ads.android.UnityAdsUtils;
import com.unity3d.ads.android.properties.UnityAdsConstants;
import com.unity3d.ads.android.properties.UnityAdsProperties;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class UnityAdsDevice {
	public static String getSoftwareVersion () {
		return "" + Build.VERSION.SDK_INT;
	}
	
	public static String getHardwareVersion () {
		return Build.MANUFACTURER + " " + Build.MODEL;
	}
	
	public static int getDeviceType () {
		return UnityAdsProperties.getCurrentActivity().getResources().getConfiguration().screenLayout;
	}

	@SuppressLint("DefaultLocale")
	public static String getAndroidId (boolean md5hashed) {
		return "";
	}
	
	public static String getAndroidSerial () {
		String androidSerial = UnityAdsConstants.UNITY_ADS_DEVICEID_UNKNOWN;
		
		try {
	        Class<?> c = Class.forName("android.os.SystemProperties");
	        Method get = c.getMethod("get", String.class);
	        androidSerial = (String) get.invoke(c, "ro.serialno");
	        androidSerial = UnityAdsUtils.Md5(androidSerial);
	        androidSerial = androidSerial.toLowerCase();
	    } 
		catch (Exception e) {
	    }
		
		return androidSerial;
	}
	
    public static String getMacAddress() {
		NetworkInterface intf = null;
		intf = getInterfaceFor("eth0");		
		if (intf == null) {
			intf = getInterfaceFor("wlan0");
		}
		
		return buildMacAddressFromInterface(intf);
    }
        
    public static String getAdvertisingTrackingId() {
    	return UnityAdsAdvertisingId.getAdvertisingTrackingId();
    }
    
    public static boolean isLimitAdTrackingEnabled() {
    	return UnityAdsAdvertisingId.getLimitedAdTracking();
    }
	
    @SuppressLint("DefaultLocale")
	public static String buildMacAddressFromInterface (NetworkInterface intf) {
		byte[] mac = null;
		
		if (intf == null) {
			return UnityAdsConstants.UNITY_ADS_DEVICEID_UNKNOWN;
		}
		
		try
		{
			Method layertype = NetworkInterface.class.getMethod("getHardwareAddress");
			mac = (byte[])layertype.invoke(intf);
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Could not getHardwareAddress");
		}
		
		if (mac == null) {
			return UnityAdsConstants.UNITY_ADS_DEVICEID_UNKNOWN;
		}
		
		StringBuilder buf = new StringBuilder();
        for (int idx=0; idx<mac.length; idx++)
            buf.append(String.format("%02X:", mac[idx]));       
        if (buf.length()>0) buf.deleteCharAt(buf.length()-1);
        
        String retMacAddress = UnityAdsUtils.Md5(buf.toString());
        return retMacAddress.toLowerCase();    	
    }
    
    public static NetworkInterface getInterfaceFor (String interfaceName) {
    	List<NetworkInterface> interfaces = null;
    	
        try {
        	interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        }
        catch (Exception e) {
        	return null;
        }
        
    	for (NetworkInterface intf : interfaces) {
    		if (interfaceName != null) {
    			if (intf.getName().equalsIgnoreCase(interfaceName)) {
    				UnityAdsDeviceLog.debug("Returning interface: " + intf.getName());
    				return intf;
    			}
    				
            }
    	}
        
    	return null;
    }
	
	public static String getConnectionType () {
		if (isUsingWifi()) {
			return "wifi";
		}
		
		return "cellular";
	}
	
	@SuppressWarnings("deprecation")
	public static boolean isUsingWifi () {
		ConnectivityManager mConnectivity = null;
		Activity act = UnityAdsProperties.getCurrentActivity();
		
		if (act == null) return false;
		
		mConnectivity = (ConnectivityManager)act.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (mConnectivity == null)
			return false;

		TelephonyManager mTelephony = null;
		
		if (act != null) {
			mTelephony = (TelephonyManager)act.getSystemService(Context.TELEPHONY_SERVICE);
		}
			 
		// Skip if no connection, or background data disabled
		NetworkInfo info = mConnectivity.getActiveNetworkInfo();
		if (info == null || !mConnectivity.getBackgroundDataSetting() || !mConnectivity.getActiveNetworkInfo().isConnected() || mTelephony == null) {
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

	public static int getNetworkType() {
		Activity activity = UnityAdsProperties.getCurrentActivity();

		if(activity != null) {
			TelephonyManager tm = (TelephonyManager)activity.getSystemService(Context.TELEPHONY_SERVICE);

			return tm.getNetworkType();
		}

		return TelephonyManager.NETWORK_TYPE_UNKNOWN;
	}

	public static int getScreenDensity() {
		return UnityAdsProperties.getCurrentActivity().getResources().getDisplayMetrics().densityDpi;
	}

	public static int getScreenSize() {
		return getDeviceType();
	}

	public static JSONArray getPackageJsonArray(Map<String,String> whitelist) {
		if(whitelist == null || whitelist.size() == 0) return null;

		Activity activity = UnityAdsProperties.getCurrentActivity();

		if(activity == null) return null;

		PackageManager pm = activity.getPackageManager();
		JSONArray pkgList = null;

		for(PackageInfo pkg : pm.getInstalledPackages(0)) {
			try {
				if(pkg.packageName != null && pkg.packageName.length() > 0) {
					String md5pkg = UnityAdsUtils.Md5(pkg.packageName);

					if(whitelist.containsKey(md5pkg)) {
						whitelist.get(md5pkg);
						JSONObject jsonEntry = new JSONObject();

						jsonEntry.put("id", whitelist.get(md5pkg));
						if(pkg.firstInstallTime > 0) {
							jsonEntry.put("timestamp", pkg.firstInstallTime);
						}

						if(pkgList == null) pkgList = new JSONArray();
						pkgList.put(jsonEntry);
					}
				}
			} catch(Exception e) {
				UnityAdsDeviceLog.debug("Exception when processing package " + pkg.packageName + " " + e);
			}
		}

		return pkgList;
	}

	public static String getPackageDataJson(Map<String,String> whitelist) {
		JSONArray packages = getPackageJsonArray(whitelist);
		if(packages == null) return null;

		JSONObject wrapper = new JSONObject();
		try {
			wrapper.put("games", packages);
			return wrapper.toString();
		} catch(Exception e) {
			UnityAdsDeviceLog.debug("Exception in getPackageDataJson" + e);
			return null;
		}
	}
}