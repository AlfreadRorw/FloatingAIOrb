package com.alfread.alfvision;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 501;
    private EditText apiKey;
    private EditText model;
    private EditText systemPrompt;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(10, 10, 15));

        TextView title = text("ALF Vision Panel", 28, Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text(
                "Floating AI yang bisa melihat area layar pilihan lu dan menjawab pertanyaan berdasarkan gambar.",
                14, Color.LTGRAY);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(6);
        root.addView(sub, subLp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(22), 0, dp(20));
        scroll.addView(content);

        content.addView(label("GROQ API KEY"));
        apiKey = edit("gsk_...", true);
        apiKey.setText(SecureStore.get(this));
        content.addView(apiKey, fieldLp());

        content.addView(label("VISION MODEL"));
        model = edit("qwen/qwen3.8-27b", false);
        model.setText(getSharedPreferences("settings", 0)
                .getString("model", "qwen/qwen3.8-27b"));
        content.addView(model, fieldLp());

        content.addView(label("SYSTEM PROMPT"));
        systemPrompt = edit("Instruksi AI...", false);
        systemPrompt.setText(getSharedPreferences("settings", 0).getString(
                "prompt",
                "Kamu adalah ALF Vision, asisten AI yang melihat screenshot area yang dipilih user. "
                        + "Jawab dalam bahasa Indonesia dengan jelas, praktis, dan langsung ke inti. "
                        + "Jika melihat error atau UI aplikasi, jelaskan apa yang terlihat dan langkah berikutnya."));
        systemPrompt.setMinLines(5);
        content.addView(systemPrompt, fieldLp());

        Button save = button("Simpan Pengaturan");
        save.setOnClickListener(v -> {
            SecureStore.put(this, apiKey.getText().toString().trim());
            getSharedPreferences("settings", 0).edit()
                    .putString("model", model.getText().toString().trim())
                    .putString("prompt", systemPrompt.getText().toString().trim())
                    .apply();
            Toast.makeText(this, "Pengaturan disimpan", Toast.LENGTH_SHORT).show();
        });
        content.addView(save, buttonLp());

        Button overlay = button("Izin Floating Panel");
        overlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                Toast.makeText(this, "Izin floating sudah aktif", Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(overlay, buttonLp());

        Button start = button("Mulai ALF Vision");
        start.setOnClickListener(v -> startVision());
        content.addView(start, buttonLp());

        Button stop = button("Hentikan ALF Vision");
        stop.setOnClickListener(v ->
                stopService(new Intent(this, OverlayService.class)));
        content.addView(stop, buttonLp());

        TextView info = text(
                "Cara pakai:\n"
                        + "1. Masukkan Groq API key.\n"
                        + "2. Aktifkan izin floating.\n"
                        + "3. Tekan Mulai ALF Vision dan izinkan screen capture.\n"
                        + "4. Geser/ubah ukuran kotak area layar.\n"
                        + "5. Buka panel AI, ketik pertanyaan, lalu kirim.\n\n"
                        + "Screenshot hanya diambil ketika lu menekan tombol analisis.",
                13, Color.GRAY);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(18);
        content.addView(info, infoLp);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
    }

    private void startVision() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan izin floating terlebih dahulu", Toast.LENGTH_LONG).show();
            return;
        }
        if (SecureStore.get(this).isEmpty()) {
            Toast.makeText(this, "Masukkan Groq API key terlebih dahulu", Toast.LENGTH_LONG).show();
            return;
        }

        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE || data == null ||
                resultCode != RESULT_OK) {
            return;
        }

        Intent service = new Intent(this, OverlayService.class);
        service.putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(OverlayService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        Toast.makeText(this, "ALF Vision aktif", Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView label(String value) {
        TextView t = text(value, 12, Color.LTGRAY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        return t;
    }

    private EditText edit(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setTextSize(14);
        e.setSingleLine(!password);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(25, 25, 32));
        bg.setCornerRadius(dp(12));
        e.setBackground(bg);
        if (password) {
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return e;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        return lp;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(45, 35, 75));
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(10);
        return lp;
    }
}
