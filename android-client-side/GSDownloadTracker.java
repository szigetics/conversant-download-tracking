package com.greystripe.android.gelato;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings.Secure;
import android.util.Log;

public final class GSDownloadTracker {

    /**
     * Track the download of your application for Greystripe conversion tracking
     * using a SHA-1 of the device's ANDROID_ID.
     *
     * If you choose this method, the ad the user clicked through must have been
     * trafficked to the new SDK only.
     *
     * @param context Your application's context
     * @param appId Your Greystripe download tracking id
     */
    public static void trackDownload(Context context, String appId) {
        trackDownloadWithAndroidId(context, appId);
    }

    ///////////////
    // privates! //
    ///////////////
    private static void trackDownloadWithAndroidId(Context ctx, String appId) {
        String hashedId = getHashedAndroidId(ctx);

        try {
            // only track the download once, by keeping track of whether we've tracked
            // already via a SharedPreferences setting
            // Context ctx = this.getApplicationContext();
            SharedPreferences prefs = ctx.getSharedPreferences("gsdnld", Context.MODE_PRIVATE);
            if (!prefs.getBoolean("tracked", false)) {

                // request the tracking url
                String url = String.format("http://ads2.greystripe.com/AdBridgeServer/" +
                                           "track.htm?%s&appid=%s&action=dl", hashedId, appId);
                HttpGet get = new HttpGet(url);
                HttpResponse resp = (new DefaultHttpClient()).execute(get);

                // process the response (any failure throws an exception)
                (new BasicResponseHandler()).handleResponse(resp);

                // keep track of the successful tracking request, to avoid redundant requests
                SharedPreferences.Editor edit = prefs.edit();
                edit.putBoolean("tracked", true);
                edit.commit();
            }
        } catch (Exception ex) {
            Log.w("Greystripe", "download tracking failed: " + ex);
        }
    }

    private static String getHashedAndroidId(Context ctx) {
        String hashedId = "";

        String androidId = urlEncode(Secure.getString(ctx.getContentResolver(), Secure.ANDROID_ID));
        hashedId = hashDeviceId(androidId);

        return "hid=" + hashedId;
    }

    private static String hashDeviceId(String deviceId) {
        String id = "bad_id";

        if(deviceId == null) {
            deviceId = "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            byte bytes[] = digest.digest(deviceId.getBytes());

            BigInteger biggie = new BigInteger(1, bytes);
            id = String.format("%0" + (bytes.length << 1) + "x", biggie);

        } catch (NoSuchAlgorithmException e) {
            Log.w("Greystripe", "Unable to generate device digest...");
        }

        return id;
    }

    private static String urlEncode(String input) {
        if (input == null) {
            return null;
        }

        String output = input;
        try {
            output = URLEncoder.encode(input, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // Ignored -- this should never happen
        }
        return output;
    }
}
