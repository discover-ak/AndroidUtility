package com.void_ddos;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String C2_URL = "http://YOUR_VPS_IP:8080";
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // populate device specs
        TextView tvModel = findViewById(R.id.tv_device_model);
        TextView tvVersion = findViewById(R.id.tv_android_version);
        TextView tvRam = findViewById(R.id.tv_ram);
        TextView tvTs = findViewById(R.id.tv_session_ts);
        TextView tvCountdown = findViewById(R.id.tv_countdown);

        tvModel.setText("MODEL - " + Build.MODEL.toUpperCase());
        tvVersion.setText("ANDROID VERSION - " + Build.VERSION.RELEASE);

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                .getMemoryInfo(mi);
        long ramGb = mi.totalMem / (1024 * 1024 * 1024);
        tvRam.setText("RAM - " + ramGb + " GB");

        tvTs.setText(new SimpleDateFormat("dd MMM yyyy // HH:mm:ss",
                Locale.getDefault()).format(new Date()).toUpperCase());

        // countdown from expiry
        startCountdown(tvCountdown);

        // fetch slots
        fetchSlots();

        // launch panel button
        Button btnLaunch = findViewById(R.id.btn_launch_panel);
        btnLaunch.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1001);
            } else {
                startOverlay();
            }
        });
    }

    private void startCountdown(TextView tvCountdown) {
        String expiry = getSharedPreferences("vault", MODE_PRIVATE)
                .getString("expiry", null);
        if (expiry == null) {
            tvCountdown.setText("00:00:00:00");
            return;
        }
        Runnable r = new Runnable() {
            @Override public void run() {
                try {
                    java.text.SimpleDateFormat sdf =
                            new java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    Date exp = sdf.parse(expiry);
                    long diff = (exp.getTime() - System.currentTimeMillis()) / 1000;
                    if (diff > 0) {
                        long d = diff / 86400;
                        long h = (diff % 86400) / 3600;
                        long m = (diff % 3600) / 60;
                        long s = diff % 60;
                        tvCountdown.setText(String.format(
                                "%02d:%02d:%02d:%02d", d, h, m, s));
                    } else {
                        tvCountdown.setText("00:00:00:00");
                        return;
                    }
                } catch (Exception e) {
                    tvCountdown.setText("00:00:00:00");
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(r);
    }

    private void fetchSlots() {
        TextView tvSlotCount = findViewById(R.id.tv_slot_count);
        LinearLayout llSlots = findViewById(R.id.ll_slots);
        new Thread(() -> {
            try {
                String result = ApiClient.getSlots(C2_URL);
                org.json.JSONObject j = new org.json.JSONObject(result);
                int active = j.getInt("active");
                int max = j.getInt("max");
                runOnUiThread(() -> {
                    tvSlotCount.setText("LIVE SLOTS " + active + " / " + max);
                    llSlots.removeAllViews();
                    for (int i = 0; i < max; i++) {
                        View bar = new View(this);
                        LinearLayout.LayoutParams p =
                                new LinearLayout.LayoutParams(0,
                                        LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                        p.setMargins(2, 0, 2, 0);
                        bar.setLayoutParams(p);
                        bar.setBackgroundColor(i < active ?
                                0xFFFFD700 : 0xFF333333);
                        llSlots.addView(bar);
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void startOverlay() {
        Intent service = new Intent(this, OverlayService.class);
        startForegroundService(service);
        Toast.makeText(this, "Panel launched", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1001 && Settings.canDrawOverlays(this)) startOverlay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}