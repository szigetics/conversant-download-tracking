//
//  CMDownloadTracker.m
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

#import "CMDownloadTracker.h"

#include <AdSupport/AdSupport.h>
#include <CommonCrypto/CommonDigest.h>
#include <sys/socket.h>
#include <net/if.h>
#include <net/if_dl.h>
#include <sys/sysctl.h>

@implementation CMDownloadTracker

/**
 * Get the hashed MAC address of the device
 */
+ (NSString *)hashedMACAddress
{
    int                 mgmtInfoBase[6];
    char                *msgBuffer = NULL;
    size_t              length;
    unsigned char       macAddress[6];
    struct if_msghdr    *interfaceMsgStruct;
    struct sockaddr_dl  *socketStruct;
    NSString            *errorFlag = NULL;
    
    // Setup the management Information Base (mib)
    mgmtInfoBase[0] = CTL_NET;        // Request network subsystem
    mgmtInfoBase[1] = AF_ROUTE;       // Routing table info
    mgmtInfoBase[2] = 0;
    mgmtInfoBase[3] = AF_LINK;        // Request link layer information
    mgmtInfoBase[4] = NET_RT_IFLIST;  // Request all configured interfaces
    
    // With all configured interfaces requested, get handle index
    if((mgmtInfoBase[5] = if_nametoindex("en0")) == 0)
        errorFlag = @"if_nametoindex failure";
    else
    {
        // Get the size of the data available (store in len)
        if(sysctl(mgmtInfoBase, 6, NULL, &length, NULL, 0) < 0)
            errorFlag = @"sysctl mgmtInfoBase failure";
        else
        {
            // Alloc memory based on above call
            if((msgBuffer = malloc(length)) == NULL)
                errorFlag = @"buffer allocation failure";
            else
            {
                // Get system information, store in buffer
                if(sysctl(mgmtInfoBase, 6, msgBuffer, &length, NULL, 0) < 0)
                    errorFlag = @"sysctl msgBuffer failure";
            }
        }
    }
    
    // Before going any further...
    if(errorFlag != NULL)
    {
        return errorFlag;
    }
    
    // Map msgbuffer to interface message structure
    interfaceMsgStruct = (struct if_msghdr *) msgBuffer;
    
    // Map to link-level socket structure
    socketStruct = (struct sockaddr_dl *) (interfaceMsgStruct + 1);
    
    // Copy link layer address data in socket structure to an array
    memcpy(&macAddress, socketStruct->sdl_data + socketStruct->sdl_nlen, 6);
    
    // Read from char array into a string object, into traditional Mac address format
    NSString *macAddressString = [NSString stringWithFormat:@"%02X:%02X:%02X:%02X:%02X:%02X",
                                  macAddress[0], macAddress[1], macAddress[2],
                                  macAddress[3], macAddress[4], macAddress[5]];
    
    // Release the buffer memory
    free(msgBuffer);
    
    //hash the mac address
    unsigned char digest[CC_SHA1_DIGEST_LENGTH];
    NSData * idData = [macAddressString dataUsingEncoding:NSUTF8StringEncoding];
    if(CC_SHA1([idData bytes], [idData length], digest))
    {
        NSData * hashedData = [NSData dataWithBytes:digest length:CC_SHA1_DIGEST_LENGTH];
        NSCharacterSet * charsToRemove = [NSCharacterSet characterSetWithCharactersInString:@"<>"];
        NSString * hashedIDString = [[hashedData description] stringByTrimmingCharactersInSet:charsToRemove];
        return [hashedIDString stringByReplacingOccurrencesOfString:@" " withString:@""];
    }
    return nil;
}

+ (void)trackInternalWithAppID:(NSString *)a_appID
{
    NSAutoreleasePool * pool = [[NSAutoreleasePool alloc] init];
    // first check to see whether we've already informed Conversant of this download
    NSFileManager * fm = [NSFileManager defaultManager];
    NSString * cachesPath = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0];
    NSString * filePath = [cachesPath stringByAppendingPathComponent:[NSString stringWithFormat:@"gsDownloadTracked-%@", a_appID]];
    if(![fm fileExistsAtPath:filePath])
    {
        NSString * hashedMAC = [self hashedMACAddress];
        NSString * idfa = @"";
        if([[UIDevice currentDevice] respondsToSelector:@selector(identifierForVendor)] && NSClassFromString(@"ASIdentifierManager"))
        {
            idfa = [[[ASIdentifierManager sharedManager] advertisingIdentifier] UUIDString];
        }
        NSString * requestURLString = [NSString stringWithFormat:@"http://ads2.greystripe.com/AdBridgeServer/track.htm?hmid=%@&idfa=%@&appid=%@&action=dl", hashedMAC, idfa, a_appID];
        NSURLRequest * request  = [NSURLRequest requestWithURL:[NSURL URLWithString:requestURLString]];
        NSHTTPURLResponse * response = nil;
        NSError * errors = nil;
        NSData * result = [NSURLConnection sendSynchronousRequest:request returningResponse:&response error:&errors];
        if(result != nil && errors == nil && [response statusCode] == 200)
        {
            // Conversant has successfully been informed of the download. Write out
            // a dummy file to the filesystem so that we no longer inform
            // Conversant when this function is called.
            [fm createFileAtPath:filePath contents:nil attributes:nil];
        }
    }
    
    // clean up
    [pool release];
}

+ (void)trackWithAppID:(NSString *)a_appID
{
    if(a_appID == nil)
    {
        [NSException raise:@"Invalid application identifier" format:@"You must provide a valid application identifier to track a download."];
    }
    else
    {
        [[CMDownloadTracker class] performSelectorInBackground:@selector(trackInternalWithAppID:) withObject:a_appID];
    }
}

@end
