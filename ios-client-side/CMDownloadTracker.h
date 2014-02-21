//
//  CMDownloadTracker.h
//
//  Copyright 2014 Conversant, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#import <Foundation/Foundation.h>

@interface CMDownloadTracker : NSObject

/**
 * Track the download of this game on the current user's device using
 * Conversant downloading tracking.
 */
+ (void)trackWithAppID:(NSString *)a_appID;

@end
