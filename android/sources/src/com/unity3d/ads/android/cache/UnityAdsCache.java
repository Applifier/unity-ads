package com.unity3d.ads.android.cache;

import android.annotation.TargetApi;
import android.os.Build;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.unity3d.ads.android.UnityAdsDeviceLog;
import com.unity3d.ads.android.campaign.UnityAdsCampaign;
import com.unity3d.ads.android.properties.UnityAdsConstants;
import com.unity3d.ads.android.properties.UnityAdsProperties;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class UnityAdsCache {
	private static File _cacheDirectory = null;

	public static void initialize(ArrayList<UnityAdsCampaign> campaigns) {
		if(campaigns == null || campaigns.size() == 0) return;

		UnityAdsDeviceLog.debug("Unity Ads cache: initializing cache with " + campaigns.size() + " campaigns");

		stopAllDownloads();

		HashMap<String,String> downloadFiles = new HashMap<>();
		HashMap<String,Long> allFiles = new HashMap<>();

		boolean first = true;
		for(UnityAdsCampaign campaign : campaigns) {
			// Note: Always cache first video in ad plan if allowCache flag is true for that video.
			// Usually server forces first video to be cached but if ads are filtered due to app install check,
			// server has no idea what is actually the first video after filtering
			if(campaign.forceCacheVideo() || (campaign.allowCacheVideo() && first)) {
				String filename = campaign.getVideoFilename();

				if(!isFileCached(filename, campaign.getVideoFileExpectedSize())) {
					UnityAdsDeviceLog.debug("Unity Ads cache: queuing " + filename + " for download");
					downloadFiles.put(campaign.getVideoUrl(), filename);
				} else {
					UnityAdsDeviceLog.debug("Unity Ads cache: not downloading " + filename + ", already in cache");
				}
			}

			allFiles.put(campaign.getVideoFilename(), campaign.getVideoFileExpectedSize());

			first = false;
		}

		initializeCacheDirectory(allFiles);

		for(Map.Entry<String,String> entry : downloadFiles.entrySet()) {
			UnityAdsCacheThread.download(entry.getKey(), getFullFilename(entry.getValue()));
		}
	}

	public static void cacheCampaign(UnityAdsCampaign campaign) {
		String filename = campaign.getVideoFilename();
		long size = campaign.getVideoFileExpectedSize();

		// Check if video is already in cache
		if(isFileCached(filename, size)) return;

		String currentDownload = UnityAdsCacheThread.getCurrentDownload();

		// Check if video is currently downloaded
		if(currentDownload != null && currentDownload.equals(getFullFilename(filename))) return;

		UnityAdsCacheThread.download(campaign.getVideoUrl(), getFullFilename(filename));
	}

	public static boolean isCampaignCached(UnityAdsCampaign campaign) {
		String filename = campaign.getVideoFilename();
		long size = campaign.getVideoFileExpectedSize();

		return isFileCached(filename, size);
	}

	public static void stopAllDownloads() {
		UnityAdsCacheThread.stopAllDownloads();
	}

	private static void initializeCacheDirectory(HashMap<String,Long> files) {
		_cacheDirectory = new File(UnityAdsProperties.APPLICATION_CONTEXT.getFilesDir().getPath());

		if (Build.VERSION.SDK_INT > 18) {
			File externalCacheFile = UnityAdsProperties.APPLICATION_CONTEXT.getExternalCacheDir();
			if (externalCacheFile != null) {

				String absoluteCachePath;
				absoluteCachePath = externalCacheFile.getAbsolutePath();
				_cacheDirectory = new File(absoluteCachePath, UnityAdsConstants.CACHE_DIR_NAME);
				if (_cacheDirectory.mkdirs()) {
					UnityAdsDeviceLog.debug("Successfully created cache");
				}
			}
		}

		UnityAdsDeviceLog.debug("Unity Ads cache: using " + _cacheDirectory.getAbsolutePath() + " as cache");

		if(!_cacheDirectory.isDirectory()) {
			UnityAdsDeviceLog.error("Unity Ads cache: Creating cache dir failed");
			return;
		}

		// Don't delete pending events
		files.put(UnityAdsConstants.PENDING_REQUESTS_FILENAME, (long)-1);

		File[] fileList;

		if(_cacheDirectory.getAbsolutePath().endsWith(UnityAdsConstants.CACHE_DIR_NAME)) {
			UnityAdsDeviceLog.debug("Unity Ads cache: checking cache directory " + _cacheDirectory.getAbsolutePath());
			fileList = _cacheDirectory.listFiles();
		} else {
			UnityAdsDeviceLog.debug("Unity Ads cache: checking app directory for Unity Ads cached files");
			FilenameFilter filter = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String filename) {
					boolean filter = filename.startsWith(UnityAdsConstants.UNITY_ADS_LOCALFILE_PREFIX);
					UnityAdsDeviceLog.debug("Unity Ads cache: filtering result for file: " + filename + ", " + filter);
					return filter;
				}
			};

			fileList = _cacheDirectory.listFiles(filter);
		}

		if(fileList == null) {
			UnityAdsDeviceLog.error("Unity Ads cache: Could not obtain fileList from cache");
			return;
		}

		for(File cacheFile : fileList) {
			String name = cacheFile.getName();

			if(!files.containsKey(name)) {
				UnityAdsDeviceLog.debug("Unity Ads cache: " + name + " not found in ad plan, deleting from cache");
				boolean success = cacheFile.delete();
				if (!success) UnityAdsDeviceLog.debug("Unity Ads cache: Couldn't delete file: " + cacheFile.getAbsolutePath());
			} else {
				long expectedSize = files.get(name);

				if(expectedSize != -1) {
					long size = cacheFile.length();

					if(size != expectedSize) {
						UnityAdsDeviceLog.debug("Unity Ads cache: " + name + " file size mismatch, deleting from cache");
						boolean success = cacheFile.delete();
						if (!success) UnityAdsDeviceLog.debug("Unity Ads cache: Couldn't delete file: " + cacheFile.getAbsolutePath());
					} else {
						UnityAdsDeviceLog.debug("Unity Ads cache: " + name + " found, keeping");
					}
				}
			}
		}
	}

	public static String getCacheDirectory() {
		return _cacheDirectory != null ? _cacheDirectory.getAbsolutePath() : null;
	}

	private static String getFullFilename(String filename) {
		return getCacheDirectory() + "/" + filename;
	}

	private static boolean isFileCached(String file, long size) {
		File cacheFile = new File(getCacheDirectory() + "/" + file);

		if(cacheFile.exists()) {
			if(size == -1 || cacheFile.length() == size) {
				return true;
			}
		}

		return false;
	}
}