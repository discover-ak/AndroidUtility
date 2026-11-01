package com.void_ddos;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LoginActivity extends Activity {

    private static final String C2_URL = "http://YOUR_VPS_IP:8080";
    private EditText etKey;
    private Button btnLogin, btnPaste;
    private TextView tvStatus;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etKey = findViewById(R.id.et_key);
        btnLogin = findViewById(R.id.btn_login);
        btnPaste = findViewById(R.id.btn_paste);
        tvStatus = findViewById(R.id.tv_connect_status);

        // update time
        TextView tvTime = findViewById(R.id.tv_time);
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date()));

        // check saved key — skip login
        String savedKey = getSharedPreferences("vault", MODE_PRIVATE)
                .getString("active_key", null);
        if (savedKey != null) {
            goToMain();
            return;
        }

        btnPaste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip() &&
                    cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip()
                        .getItemAt(0).getText();
                if (text != null) etKey.setText(text.toString());
            }
        });

        etKey.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                btnLogin.setEnabled(s.length() > 0);
                if (s.length() > 0) {
                    btnLogin.setBackgroundResource(R.drawable.btn_gold);
                    btnLogin.setTextColor(0xFF000000);
                } else {
                    btnLogin.setBackgroundResource(R.drawable.btn_dark);
                    btnLogin.setTextColor(0xFF555555);
                }
            }
        });

        btnLogin.setOnClickListener(v -> {
            String key = etKey.getText().toString().trim();
            if (key.isEmpty()) return;
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("> Connecting to server...");
            btnLogin.setEnabled(false);
            validateKey(key);
        });
    }

    private void validateKey(String key) {
        KeyValidator.checkKey(C2_URL, key, (valid, plan, expiry, reason) -> {
            runOnUiThread(() -> {
                if (valid) {
                    tvStatus.setText("> Authentication successful...");
                    getSharedPreferences("vault", MODE_PRIVATE)
                            .edit()
                            .putString("active_key", key)
                            .putString("expiry", expiry)
                            .apply();
                    handler.postDelayed(this::goToMain, 800);
                } else {
                    tvStatus.setText("> " + reason);
                    tvStatus.setTextColor(0xFFFF4444);
                    btnLogin.setEnabled(true);
                }
            });
        });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}