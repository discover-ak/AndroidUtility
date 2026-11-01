package com.void_ddos;

import java.io.*;

public class IpCapture {

    public interface IpCallback {
        void onResult(String ip);
    }

    public static void getGameServerIp(IpCallback callback) {
        new Thread(() -> {
            String ip = readProcUdp();
            callback.onResult(ip);
        }).start();
    }

    private static String readProcUdp() {
        // try udp first then udp6
        String result = parseUdpFile("/proc/net/udp");
        if (result != null) return result;
        return parseUdpFile("/proc/net/udp6");
    }

    private static String parseUdpFile(String path) {
        try {
            BufferedReader br = new BufferedReader(
                    new FileReader(path));
            String line;
            br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) continue;
                String remoteHex = parts[2];
                String[] remoteAddrPort = remoteHex.split(":");
                if (remoteAddrPort.length < 2) continue;

                long portHex = Long.parseLong(
                        remoteAddrPort[1], 16);
                // BGMI game server port range
                if (portHex >= 10000 && portHex <= 20000) {
                    String ipHex = remoteAddrPort[0];
                    long ipLong = Long.parseLong(ipHex, 16);
                    int b1 = (int) (ipLong & 0xFF);
                    int b2 = (int) ((ipLong >> 8) & 0xFF);
                    int b3 = (int) ((ipLong >> 16) & 0xFF);
                    int b4 = (int) ((ipLong >> 24) & 0xFF);
                    br.close();
                    return b1 + "." + b2 + "." + b3 + "." + b4;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return null;
    }
}