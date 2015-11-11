//
//  UnityAdsCacheOperation.m
//  testApp
//
//  Created by Sergey D on 3/10/14.
//  Copyright (c) 2014 Unity Technologies. All rights reserved.
//

#import "UnityAdsCacheFileOperation.h"

@interface UnityAdsCacheFileOperation () <NSURLConnectionDelegate> {
@private
  NSFileHandle * _fileHandle;
  NSURLConnection * _connection;
  BOOL _finished;
  BOOL _cancelEventSent;
}

@end

@implementation UnityAdsCacheFileOperation

- (BOOL)isExecuting {
  @synchronized(self) {
    return !_finished && !_cancelEventSent;
  }
}

- (BOOL)isFinished {
  @synchronized (self) {
    return _finished || _cancelEventSent;
  }
}

- (void)threadBlocked:(BOOL (^)())isThreadBlocked {
	@autoreleasepool {
		NSPort *port = [[NSPort alloc] init];
		[port scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
		
		while(isThreadBlocked()) {
			@autoreleasepool {
				[[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                 beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.5]];
			}
		}
	}
}

- (void)cancel {
  [super cancel];
  if ([self.delegate respondsToSelector:@selector(cacheOperationCancelled:)]) {
    @synchronized(self) {
      _cancelEventSent = YES;
    }
    [self.delegate cacheOperationCancelled:self];
  }
}

- (void)main {
  if ([self.delegate respondsToSelector:@selector(cacheOperationStarted:)])
    [self.delegate cacheOperationStarted:self];
  
  NSTimeInterval cachingDuration = 0.0;
  
  NSDictionary *attributes = [[NSFileManager defaultManager] attributesOfItemAtPath:self.filePath error:nil];
  long long size = [attributes fileSize];
  if (size != self.expectedFileSize) {
    BOOL isDir = NO;
    if ([[NSFileManager defaultManager] fileExistsAtPath:self.filePath]) {
      [[NSFileManager defaultManager] removeItemAtPath:self.filePath error:nil];
    }
    if (![[NSFileManager defaultManager] fileExistsAtPath:self.directoryPath isDirectory:&isDir]) {
      [[NSFileManager defaultManager] createDirectoryAtPath:self.directoryPath
                                withIntermediateDirectories:YES
                                                 attributes:nil
                                                      error:nil];
    }
    [[NSFileManager defaultManager] createFileAtPath:self.filePath contents:nil attributes:nil];
    _fileHandle = [NSFileHandle fileHandleForWritingAtPath:self.filePath];
    NSURLRequest * request = [NSURLRequest requestWithURL:self.downloadURL];
    
    NSDate *startDate = [NSDate date];
    _connection = [[NSURLConnection alloc] initWithRequest:request delegate:self];
    [self threadBlocked:^BOOL{
      @synchronized(self) {
        if([self isCancelled]) [_connection cancel];
        return ![self isFinished] && ![self isCancelled] && !_cancelEventSent;
      }
    }];
    NSDate *endDate = [NSDate date];
    cachingDuration = [endDate timeIntervalSinceDate:startDate];
  }
  
  @synchronized(self) {
    _finished = ![self isCancelled] || _cancelEventSent;
  }
  
  [_fileHandle closeFile];
  _fileHandle = nil;
  
  attributes = [[NSFileManager defaultManager] attributesOfItemAtPath:self.filePath error:nil];
  size = [attributes fileSize];

  if(cachingDuration > 0.0) {
    [self setCachingSpeed:size / cachingDuration / 1000];
  }  
  
  @synchronized (self) {
    if ([self isCancelled] && !_cancelEventSent) {
      if ([self.delegate respondsToSelector:@selector(cacheOperationCancelled:)])
        [self.delegate cacheOperationCancelled:self];
    }
  }
  
  if (size == self.expectedFileSize) {
    if ([self.delegate respondsToSelector:@selector(cacheOperationFinished:)])
      [self.delegate cacheOperationFinished:self];
    return;
  }
  
  if (size != self.expectedFileSize && ![self isCancelled]) {
    if ([self.delegate respondsToSelector:@selector(cacheOperationFailed:)])
      [self.delegate cacheOperationFailed:self];
    return;
  }
}

#pragma mark - NSURLConnectionDelegate

- (void)connection:(NSURLConnection *)connection didReceiveData:(NSData *)data {
  if(_fileHandle != nil){
  [_fileHandle writeData:data];
  }else{
    NSLog(@"_fileHandle returned nil in NSURLConnectionDataDelegate callback");
  }
}

- (void)connectionDidFinishLoading:(NSURLConnection *)connection {
  @synchronized(self) {
    _finished = YES;
  }
}

- (void)connection:(NSURLConnection *)connection didFailWithError:(NSError *)error {
  @synchronized(self) {
    _finished = YES;
  }
}

- (void)dealloc {
  self.downloadURL = nil;
  self.filePath = nil;
  self.directoryPath = nil;
}

@end
