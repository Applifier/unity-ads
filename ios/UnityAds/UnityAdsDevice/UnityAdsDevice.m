//
//  UnityAdsDevice.m
//  UnityAds
//
//  Created by bluesun on 10/19/12.
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#include <sys/socket.h>
#include <sys/sysctl.h>
#include <net/if.h>
#include <net/if_dl.h>
#include <CommonCrypto/CommonDigest.h>

#import <CoreTelephony/CTTelephonyNetworkInfo.h>
#import <SystemConfiguration/SystemConfiguration.h>

#import "UnityAdsDevice.h"
#import "../UnityAds.h"
#import "../UnityAdsProperties/UnityAdsConstants.h"

#import <dlfcn.h>
#import <mach-o/dyld.h>

#import <AdSupport/AdSupport.h>

/* The encryption info struct and constants are missing from the iPhoneSimulator SDK,
 * but not from the iPhoneOS or Mac OS X SDKs. Since one doesn't ever ship a Simulator
 * binary, we'll just provide the definitions here.
 */

#if TARGET_IPHONE_SIMULATOR && !defined(LC_ENCRYPTION_INFO)
#define LC_ENCRYPTION_INFO 0x21

struct encryption_info_command {
  uint32_t cmd;
  uint32_t cmdsize;
  uint32_t cryptoff;
  uint32_t cryptsize;
  uint32_t cryptid;
};
#endif

int main(int argc, char *argv[]);

@implementation UnityAdsDevice

+ (NSString *)_substringOfString:(NSString *)string toIndex:(NSInteger)index {
	if (index > [string length])
	{
		UALOG_DEBUG(@"Index %ld out of bounds for string '%@', length %lu.", (long)index, string, (unsigned long)[string length]);
		return nil;
	}
	
	return [string substringToIndex:index];
}

+ (BOOL)isJailbroken {
#if TARGET_IPHONE_SIMULATOR
  return NO;
#else
  static BOOL seen = NO;
  static BOOL cached = NO;
  
  if (seen == YES) {
    return cached;
  }
  
  seen = YES;
  BOOL isJailbroken = NO;
  
  // Check for Cydia, should cover most cases
#if 1
  isJailbroken = [[NSFileManager defaultManager] fileExistsAtPath:@"/Applications/Cydia.app"];
#else
  NSURL* url = [NSURL URLWithString:@"cydia://package/com.example.package"];
  isJailbroken = [[UIApplication sharedApplication] canOpenURL:url];
#endif
  
  if (isJailbroken == NO) {
    // Sandbox limitation check, can you actually open the file for reading?
    FILE *f = fopen("/bin/bash", "r");
    
    if (!(errno == ENOENT)) {
      isJailbroken = YES;
    }
    fclose(f);
  }
  
  cached = isJailbroken;
  return isJailbroken;
#endif
}

+ (BOOL)isEncrypted {
#ifdef TEST
  return NO;
#else   
  static BOOL seen = NO;
  static BOOL cached = NO;
  
  if (seen == YES) {
    return cached;
  }
  seen = YES;
  
  const struct mach_header *header;
  Dl_info dlinfo;
  
  /* Fetch the dlinfo for main() */
  if (dladdr(main, &dlinfo) == 0 || dlinfo.dli_fbase == NULL) {
    UALOG_DEBUG(@"Could not find main() symbol (very odd)");
    return NO;
  }
  header = dlinfo.dli_fbase;
  
  /* Compute the image size and search for a UUID */
  struct load_command *cmd = (struct load_command *) (header+1);
  
  for (uint32_t i = 0; cmd != NULL && i < header->ncmds; i++) {
    /* Encryption info segment */
    if (cmd->cmd == LC_ENCRYPTION_INFO) {
      struct encryption_info_command *crypt_cmd = (struct encryption_info_command *) cmd;
      /* Check if binary encryption is enabled */
      if (crypt_cmd->cryptid < 1) {
        /* Disabled, probably pirated */
        return NO;
      }
      
      /* Probably not pirated? */
      cached = YES;
      return YES;
    }
    
    cmd = (struct load_command *) ((uint8_t *) cmd + cmd->cmdsize);
  }
  
  /* Encryption info not found */
  return NO;
#endif
}

+ (NSString *)advertisingIdentifier {
  NSString *identifier = nil;

  Class advertisingManagerClass = NSClassFromString(@"ASIdentifierManager");

  if (advertisingManagerClass != nil) {
    identifier = [[[advertisingManagerClass sharedManager] advertisingIdentifier] UUIDString];
  }

  return identifier;
}

+ (BOOL)canUseTracking {
  Class advertisingManagerClass = NSClassFromString(@"ASIdentifierManager");
  BOOL enabled = YES;

  if (advertisingManagerClass != nil) {
    enabled = [[advertisingManagerClass sharedManager] isAdvertisingTrackingEnabled];
  }

  return enabled;
}

+ (NSString *)macAddress {
	NSString *interface = @"en0";
	int mgmtInfoBase[6];
	char *msgBuffer = NULL;
  
	// Setup the management Information Base (mib)
	mgmtInfoBase[0] = CTL_NET; // Request network subsystem
	mgmtInfoBase[1] = AF_ROUTE; // Routing table info
	mgmtInfoBase[2] = 0;
	mgmtInfoBase[3] = AF_LINK; // Request link layer information
	mgmtInfoBase[4] = NET_RT_IFLIST; // Request all configured interfaces
  
	// With all configured interfaces requested, get handle index
	if ((mgmtInfoBase[5] = if_nametoindex([interface UTF8String])) == 0) {
		UALOG_DEBUG(@"Couldn't get MAC address for interface '%@', if_nametoindex failed.", interface);
		return nil;
	}
  
	size_t length;
  
	// Get the size of the data available (store in len)
	if (sysctl(mgmtInfoBase, 6, NULL, &length, NULL, 0) < 0) {
		UALOG_DEBUG(@"Couldn't get MAC address for interface '%@', sysctl for mgmtInfoBase length failed.", interface);
		return nil;
	}
  
	// Alloc memory based on above call
	if ((msgBuffer = malloc(length)) == NULL) {
		UALOG_DEBUG(@"Couldn't get MAC address for interface '%@', malloc for %zd bytes failed.", interface, length);
		return nil;
	}
  
	// Get system information, store in buffer
	if (sysctl(mgmtInfoBase, 6, msgBuffer, &length, NULL, 0) < 0) {
		free(msgBuffer);
    
		UALOG_DEBUG(@"Couldn't get MAC address for interface '%@', sysctl for mgmtInfoBase data failed.", interface);
		return nil;
	}
  
	// Map msgbuffer to interface message structure
	struct if_msghdr *interfaceMsgStruct = (struct if_msghdr *) msgBuffer;
  
	// Map to link-level socket structure
	struct sockaddr_dl *socketStruct = (struct sockaddr_dl *) (interfaceMsgStruct + 1);
  
	// Copy link layer address data in socket structure to an array
	unsigned char macAddress[6];
	memcpy(&macAddress, socketStruct->sdl_data + socketStruct->sdl_nlen, 6);
  
	// Read from char array into a string object, into MAC address format
	NSString *macAddressString = [NSString stringWithFormat:@"%02X:%02X:%02X:%02X:%02X:%02X", macAddress[0], macAddress[1], macAddress[2], macAddress[3], macAddress[4], macAddress[5]];
  
	// Release the buffer memory
	free(msgBuffer);
  
	return macAddressString;
}

+ (NSString *)machineName {
	size_t size;
  sysctlbyname("hw.machine", NULL, &size, NULL, 0);
  char *answer = malloc(size);
	sysctlbyname("hw.machine", answer, &size, NULL, 0);
	NSString *result = [NSString stringWithCString:answer encoding:NSUTF8StringEncoding];
	free(answer);
	
	return result;
}

+ (NSArray *)getDeviceModelAsStringComponents {
  NSString *modelString = [[[UIDevice currentDevice] model] lowercaseString];
  if (modelString != nil) {
    if ([modelString rangeOfString:@" "].location != NSNotFound) {
      NSArray *components = [modelString componentsSeparatedByString:@" "];
      return components;
    }
    else {
      return [[NSArray alloc] initWithObjects:modelString, nil];
    }
  }
  
  return nil;
}

+ (BOOL)isSimulator {
  NSArray *components = [UnityAdsDevice getDeviceModelAsStringComponents];
  if (components != nil && [components count] > 0) {
    for (NSString *component in components) {
      if ([component isEqualToString:kUnityAdsDeviceSimulator]) {
        return YES;
      }
    }
  }

  return NSProcessInfo.processInfo.environment[@"SIMULATOR_DEVICE_NAME"] != nil;
}

+ (NSString *)analyticsMachineName {
  if([UnityAdsDevice isSimulator]) {
    NSArray *components = [UnityAdsDevice getDeviceModelAsStringComponents];
    if (components != nil && [components count] > 0) {
      for (NSString *component in components) {
        if ([component isEqualToString:kUnityAdsDeviceIpad]) {
          return kUnityAdsDeviceIpad;
        }
        if ([component isEqualToString:kUnityAdsDeviceIphone]) {
          return kUnityAdsDeviceIphone;
        }
        if ([component isEqualToString:kUnityAdsDeviceIpod]) {
          return kUnityAdsDeviceIpod;
        }
      }
    }
  }
  
	NSString *machine = [self machineName];
  if(machine != nil) {
    return machine;
  } else {
    return kUnityAdsDeviceIosUnknown;
  }
}

+ (NSString *)_md5StringFromString:(NSString *)string {
	if (string == nil) {
		UALOG_DEBUG(@"Input is nil.");
		return nil;
	}
	
	const char *ptr = [string UTF8String];
	unsigned char md5Buffer[CC_MD5_DIGEST_LENGTH];
	CC_MD5(ptr, (CC_LONG)strlen(ptr), md5Buffer);
	NSMutableString *output = [NSMutableString stringWithCapacity:CC_MD5_DIGEST_LENGTH * 2];
	for(int i = 0; i < CC_MD5_DIGEST_LENGTH; i++)
		[output appendFormat:@"%02x",md5Buffer[i]];
	
	return output;
}

+ (NSString *)md5MACAddressString {
	return [self _md5StringFromString:[self macAddress]];
}

+ (NSString *)md5AdvertisingIdentifierString {
	NSString *adId = [self advertisingIdentifier];
	if (adId == nil) {
		UALOG_DEBUG(@"Advertising identifier not available.");
		return nil;
	}
	
	return [self _md5StringFromString:adId];
}

static NSString* wifiConnectionString = @"wifi";
static NSString* cellularConnectionString = @"cellular";
static NSString* noneConnectionString = @"none";
static NSString* currentConnectionString = @"none";

+ (void)launchReachabilityCheck {
  __block SCNetworkReachabilityRef reachabilityRef = NULL;
  
  void (^callbackBlock)(SCNetworkReachabilityFlags) = ^(SCNetworkReachabilityFlags flags) {    
    NSString* connectionString = noneConnectionString;
    
    if ((flags & kSCNetworkReachabilityFlagsConnectionRequired) == 0) {
      // if target host is reachable and no connection is required
      //  then we'll assume (for now) that you're on Wi-Fi
      connectionString = wifiConnectionString;
    }
    
    if ((flags & kSCNetworkReachabilityFlagsConnectionOnDemand) != 0 || (flags & kSCNetworkReachabilityFlagsConnectionOnTraffic) != 0)
    {
      // ... and the connection is on-demand (or on-traffic) if the
      //     calling application is using the CFSocketStream or higher APIs
      
      if ((flags & kSCNetworkReachabilityFlagsInterventionRequired) == 0)
      {
        // ... and no [user] intervention is needed
        connectionString = wifiConnectionString;
      }
    }
    
    if ((flags & kSCNetworkReachabilityFlagsIsWWAN) != 0)
    {
      // ... but WWAN connections are OK if the calling application
      //     is using the CFNetwork (CFSocketStream?) APIs.
      connectionString = cellularConnectionString;
    }
    
    if ((flags & kSCNetworkReachabilityFlagsReachable) == 0)
    {
      // if target host is not reachable
      connectionString = noneConnectionString;
    }
    
    @synchronized(currentConnectionString) {
    currentConnectionString = connectionString;
    }
    
    if(reachabilityRef != NULL) {
      SCNetworkReachabilitySetCallback(reachabilityRef, NULL, NULL);
      SCNetworkReachabilitySetDispatchQueue(reachabilityRef, NULL);
      CFRelease(reachabilityRef);
      reachabilityRef = NULL;
    }
  };

  SCNetworkReachabilityContext context = {
    .version = 0,
    .info = (void *)CFBridgingRetain(callbackBlock),
    .release = CFRelease
  };

  reachabilityRef = SCNetworkReachabilityCreateWithName(kCFAllocatorDefault, [@"unity3d.com" UTF8String]);
  if (SCNetworkReachabilitySetCallback(reachabilityRef, UnityAdsReachabilityCallback, &context)){
    if (!SCNetworkReachabilitySetDispatchQueue(reachabilityRef, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0)) ){
      SCNetworkReachabilitySetCallback(reachabilityRef, NULL, NULL);
    }
  }
}

static void UnityAdsReachabilityCallback(SCNetworkReachabilityRef __unused ref, SCNetworkConnectionFlags flags, void* info) {
  void (^callbackBlock)(SCNetworkReachabilityFlags) = (__bridge id)info;
  callbackBlock(flags);
}

+ (NSString*)currentConnectionType {
  @synchronized(self) {
    return currentConnectionString;
  }
}

+ (NSString *)getNetworkType {
#ifdef __IPHONE_7_0
  if([self getIOSMajorVersion] >= 7) {
    CTTelephonyNetworkInfo *telephonyInfo = [CTTelephonyNetworkInfo new];
    return telephonyInfo.currentRadioAccessTechnology;
  }
  return nil;
#else
  return nil;
#endif
}

+ (NSString *)softwareVersion {
  return [[UIDevice currentDevice] systemVersion];
}

+ (NSString *)md5DeviceId {
  return [UnityAdsDevice md5AdvertisingIdentifierString];
}

+ (int)getIOSMajorVersion {
  return [[[self softwareVersion] substringToIndex:1] intValue];
}

@end
