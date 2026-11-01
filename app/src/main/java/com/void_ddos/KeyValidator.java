package com.void_ddos;

import org.json.JSONObject;
import java.io.*;
import java.net.*;

public class KeyValidator {

    public interface ValidationCallback {
        void onResult(boolean valid, String expiry,
                long expiryTs, String reason);
    }

    public static void checkKey(String baseUrl, String key,
            ValidationCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/validate");
                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty(
                        "Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("key", key);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code != 200) {
                    callback.onResult(false, null, 0,
                            "Server error: " + code);
                    return;
                }

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null)
                    sb.append(line);
                br.close();

                JSONObject resp = new JSONObject(sb.toString());
                boolean valid = resp.getBoolean("valid");

                if (valid) {
                    String expiry = resp.optString("expiry", "N/A");
                    long expiryTs = 0;
                    try {
                        java.text.SimpleDateFormat sdf =
                                new java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm:ss",
                                        java.util.Locale.getDefault());
                        expiryTs = sdf.parse(expiry).getTime() / 1000;
                    } catch (Exception e) {
                        expiryTs = System.currentTimeMillis()
                                / 1000 + 86400;
                    }
                    callback.onResult(true, expiry, expiryTs, null);
                } else {
                    String reason = resp.optString(
                            "reason", "Invalid key");
                    callback.onResult(false, null, 0, reason);
                }

            } catch (Exception e) {
                callback.onResult(false, null, 0,
                        "Connection failed: " + e.getMessage());
            }
        }).start();
    }
}