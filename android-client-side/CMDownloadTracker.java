package com.conversant.android;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.provider.Settings.Secure;
import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CMDownloadTracker {

    private static final String TAG = "Conversant";

    private static class Key {

        private Key() {
            throw new AssertionError("Key is supposed to be instantiated!");
        }

        static final String SHARED_PREFERENCES = "gsdnld";
        static final String GOOGLE_PLAY_SERVICE_ID = "gaid";
        static final String TRACKED = "tracked";
        static final String ANDROID_ID = "hid";
        static final String APP_ID = "appid";
    }

    /**
     * Track the download of your application for Conversant conversion tracking
     * using a SHA-1 of the device's ANDROID_ID.
     * <p/>
     * If you choose this method, the ad the user clicked through must have been
     * trafficked to the new SDK only.
     *
     * @param context Your application's context
     * @param appId   Your Conversant download tracking id
     */
    public static void trackDownload(Context context, String appId) {
        SharedPreferences prefs = context.getSharedPreferences(Key.SHARED_PREFERENCES, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(Key.TRACKED, false)) {
            TrackDownloadTask task = new TrackDownloadTask(appId);
            task.execute(context);
        }
    }

    private static class TrackDownloadTask extends AsyncTask<Context, Void, Void> {

        final String appId;

        public TrackDownloadTask(String appId) {
            this.appId = appId;
        }

        @Override
        protected Void doInBackground(Context... context) {
            Context appContext = context[0].getApplicationContext();
            SharedPreferences prefs = appContext.getSharedPreferences(Key.SHARED_PREFERENCES, Context.MODE_PRIVATE);
            String hashedId = getHashedAndroidId(appContext);
            try {
                if (!prefs.getBoolean(Key.TRACKED, false)) {
                    String url = getTrackUrl(hashedId, appId);
                    String id = getGoogleAdId(appContext);
                    if (id != null) {
                        url += "&" + Key.GOOGLE_PLAY_SERVICE_ID + "=" + id;
                    }
                    Log.w(TAG, url);
                    processRequest(url);
                    persistTracker(prefs);
                }
            } catch (IOException e) {
                Log.w(TAG, "Download tracking failed: " + e);
            }
            return null;
        }

        private String getTrackUrl(String hashedId, String appId) {
            StringBuilder url = new StringBuilder("http://ads2.greystripe.com/AdBridgeServer/track.htm?");
            url.append(Key.ANDROID_ID);
            url.append("=");
            url.append(hashedId);
            url.append("&");
            url.append(Key.APP_ID);
            url.append("=");
            url.append(appId);
            url.append("&action=dl");
            return url.toString();
        }

        private void processRequest(String url) throws IOException {
            HttpGet get = new HttpGet(url);
            HttpResponse resp = (new DefaultHttpClient()).execute(get);
            (new BasicResponseHandler()).handleResponse(resp);
        }

        private void persistTracker(SharedPreferences prefs) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(Key.TRACKED, true);
            edit.commit();
        }

        private String getGoogleAdId(Context context) {
            if (hasGooglePlayServicePackage()) {
                try {
                    Class<?> clazz = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient");
                    Method method = clazz.getMethod("getAdvertisingIdInfo", Context.class);
                    Object info = method.invoke(null, context);
                    Method getId = info.getClass().getMethod("getId");
                    return (String) getId.invoke(info);
                } catch (Exception e) {
                    Log.w(TAG, "Google Play Service is not available " + e.getMessage());
                    return null;
                }
            }
            return null;
        }

        private String getHashedAndroidId(Context ctx) {
            String hashedId = "";
            String androidId = urlEncode(Secure.getString(ctx.getContentResolver(), Secure.ANDROID_ID));
            hashedId = hashDeviceId(androidId);
            return hashedId;
        }

        private String hashDeviceId(String deviceId) {
            String id = "bad_id";
            if (deviceId == null) {
                deviceId = "";
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte bytes[] = digest.digest(deviceId.getBytes());
                BigInteger biggie = new BigInteger(1, bytes);
                id = String.format("%0" + (bytes.length << 1) + "x", biggie);
            } catch (NoSuchAlgorithmException e) {
                Log.w(TAG, "Unable to generate device digest...");
            }
            return id;
        }

        private String urlEncode(String input) {
            if (input == null) {
                return null;
            }
            String output = input;
            try {
                output = URLEncoder.encode(input, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // Ignored -- this should never happen
            }
            return output;
        }

        private boolean hasGooglePlayServicePackage() {
            try {
                Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    }
}
