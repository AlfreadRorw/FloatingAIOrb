package com.alfread.floatspace;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.alfread.floatspace.overlay.EdgeFloatService;
import com.alfread.floatspace.shizuku.ShizukuBridge;
import com.alfread.floatspace.util.Prefs;
import com.alfread.floatspace.util.Ui;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_NOTIFICATIONS = 73;
    private static final int REQUEST_SHIZUKU = 3401;
    private TextView overlayStatus;
    private TextView shizukuStatus;
    private TextView runningStatus;
    private SwitchCompat haptic;
    private SwitchCompat autoBoot;
    private SwitchCompat forceResizable;
    private SeekBar lengthBar;
    private RadioGroup sideGroup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(0xFF07090D);
        w.setNavigationBarColor(0xFF07090D);
        if (Build.VERSION.SDK_INT >= 23) w.getDecorView().setSystemUiVisibility(0);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07090D);
        root.addView(new BackdropView(this), new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        TextView brand = Ui.text(this, "FLOATSPACE", 25, 0xFFF4F7FB, true);
        brand.setLetterSpacing(0.12f);
        header.addView(brand, new LinearLayout.LayoutParams(-1, Ui.dp(this, 40)));
        TextView tagline = Ui.text(this, "EDGE CONTROL  /  FREEFORM WORKSPACE", 10, 0xFF8C98A9, true);
        tagline.setLetterSpacing(0.16f);
        header.addView(tagline, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
        content.addView(header);
        content.addView(Ui.spacer(this, 14));

        LinearLayout hero = Ui.card(this);
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 16));
        TextView heroTitle = Ui.text(this, "A floating workspace for Android", 20, Color.WHITE, true);
        hero.addView(heroTitle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));
        TextView heroSub = Ui.text(this,
                "Tap the slim edge line to open every launcher app. Pin your favorites, launch freeform windows, then move or resize the last window from the workspace dock.",
                12, 0xFFA4AFBE, false);
        heroSub.setLineSpacing(0, 1.12f);
        hero.addView(heroSub, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
        LinearLayout heroButtons = new LinearLayout(this);
        heroButtons.setGravity(Gravity.CENTER_VERTICAL);
        TextView start = Ui.button(this, "START EDGE", true);
        start.setOnClickListener(v -> startEdge());
        TextView stop = Ui.button(this, "STOP", false);
        stop.setOnClickListener(v -> stopEdge());
        heroButtons.addView(start, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        heroButtons.addView(stop, new LinearLayout.LayoutParams(Ui.dp(this, 95), Ui.dp(this, 48)) {{ leftMargin = Ui.dp(MainActivity.this, 8); }});
        hero.addView(heroButtons);
        content.addView(hero);
        content.addView(Ui.spacer(this, 12));

        content.addView(statusCard());
        content.addView(Ui.spacer(this, 12));
        content.addView(edgeSettingsCard());
        content.addView(Ui.spacer(this, 12));
        content.addView(engineCard());
        content.addView(Ui.spacer(this, 12));
        content.addView(utilityCard());
        content.addView(Ui.spacer(this, 18));

        TextView footer = Ui.text(this, "FloatSpace is a launcher/overlay shell. Real third-party app rendering remains the Android window manager's job.", 10, 0xFF566171, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        content.addView(footer, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        return root;
    }

    private View statusCard() {
        LinearLayout card = Ui.card(this);
        TextView title = sectionTitle("SYSTEM STATUS");
        card.addView(title);
        overlayStatus = statusRow(card, "Overlay permission", "Checking...");
        runningStatus = statusRow(card, "Edge service", "Checking...");
        shizukuStatus = statusRow(card, "Shizuku shell", "Checking...");
        LinearLayout actions = new LinearLayout(this);
        TextView overlay = Ui.button(this, "OVERLAY", false);
        overlay.setOnClickListener(v -> requestOverlay());
        TextView shizuku = Ui.button(this, "SHIZUKU", false);
        shizuku.setOnClickListener(v -> requestShizuku());
        actions.addView(overlay, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        actions.addView(shizuku, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1) {{ leftMargin = Ui.dp(MainActivity.this, 8); }});
        card.addView(actions);
        updateStatuses();
        return card;
    }

    private View edgeSettingsCard() {
        LinearLayout card = Ui.card(this);
        card.addView(sectionTitle("EDGE HANDLE"));

        TextView side = Ui.text(this, "Position", 12, 0xFFBAC4D2, true);
        card.addView(side, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
        sideGroup = new RadioGroup(this);
        sideGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton left = radio("LEFT", "left");
        RadioButton right = radio("RIGHT", "right");
        sideGroup.addView(left, new RadioGroup.LayoutParams(0, Ui.dp(this, 40), 1));
        sideGroup.addView(right, new RadioGroup.LayoutParams(0, Ui.dp(this, 40), 1));
        if ("left".equals(Prefs.get(this).getString(Prefs.EDGE_SIDE, "right"))) sideGroup.check(left.getId()); else sideGroup.check(right.getId());
        sideGroup.setOnCheckedChangeListener((g, id) -> {
            RadioButton checked = g.findViewById(id);
            if (checked != null) Prefs.get(this).edit().putString(Prefs.EDGE_SIDE, String.valueOf(checked.getTag())).apply();
            restartIfRunning();
        });
        card.addView(sideGroup);

        LinearLayout label = new LinearLayout(this);
        TextView t = Ui.text(this, "Handle length", 12, 0xFFBAC4D2, true);
        TextView value = Ui.text(this, "170 dp", 11, 0xFF8190A3, false);
        value.setGravity(Gravity.CENTER);
        label.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1));
        label.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 32)));
        card.addView(label);
        lengthBar = new SeekBar(this);
        lengthBar.setMax(220);
        lengthBar.setProgress(Prefs.get(this).getInt(Prefs.EDGE_LENGTH, 170) - 60);
        lengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean from) {
                int dp = 60 + p;
                value.setText(dp + " dp");
                Prefs.get(MainActivity.this).edit().putInt(Prefs.EDGE_LENGTH, dp).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { restartIfRunning(); }
        });
        card.addView(lengthBar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 42)));

        haptic = switchRow(card, "Haptic feedback", Prefs.get(this).getBoolean(Prefs.EDGE_HAPTIC, true));
        haptic.setOnCheckedChangeListener((buttonView, isChecked) -> Prefs.get(this).edit().putBoolean(Prefs.EDGE_HAPTIC, isChecked).apply());

        autoBoot = switchRow(card, "Start after boot", Prefs.get(this).getBoolean(Prefs.AUTO_BOOT, false));
        autoBoot.setOnCheckedChangeListener((buttonView, isChecked) -> Prefs.get(this).edit().putBoolean(Prefs.AUTO_BOOT, isChecked).apply());

        TextView themeTitle = Ui.text(this, "Glow theme", 12, 0xFFBAC4D2, true);
        themeTitle.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(themeTitle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));
        RadioGroup themes = new RadioGroup(this);
        themes.setOrientation(RadioGroup.HORIZONTAL);
        addTheme(themes, "VIOLET", "violet");
        addTheme(themes, "CYAN", "cyan");
        addTheme(themes, "GOLD", "gold");
        addTheme(themes, "ICE", "ice");
        card.addView(themes);
        return card;
    }

    private View engineCard() {
        LinearLayout card = Ui.card(this);
        card.addView(sectionTitle("WINDOW ENGINE"));
        TextView desc = Ui.text(this,
                "Shizuku runs shell commands as the shell user so Android can request real freeform activity windows. Support varies by OEM.",
                11, 0xFF8E9AAA, false);
        desc.setLineSpacing(0, 1.1f);
        card.addView(desc, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));
        forceResizable = switchRow(card, "Patch freeform + force resizable", Prefs.get(this).getBoolean(Prefs.FORCE_RESIZABLE, true));
        forceResizable.setOnCheckedChangeListener((b, checked) -> Prefs.get(this).edit().putBoolean(Prefs.FORCE_RESIZABLE, checked).apply());

        TextView learn = Ui.text(this, "Recommended: enable your device's freeform window options in Developer Options if available.",
                10, 0xFF697587, false);
        learn.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(learn);
        return card;
    }

    private View utilityCard() {
        LinearLayout card = Ui.card(this);
        card.addView(sectionTitle("TOOLS"));
        TextView battery = Ui.button(this, "BATTERY SETTINGS", false);
        battery.setOnClickListener(v -> openBatterySettings());
        TextView shizuku = Ui.button(this, "OPEN SHIZUKU", false);
        shizuku.setOnClickListener(v -> openShizuku());
        TextView stop = Ui.button(this, "STOP EDGE SERVICE", false);
        stop.setOnClickListener(v -> stopEdge());
        card.addView(battery, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        card.addView(shizuku, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)) {{ topMargin = Ui.dp(MainActivity.this, 8); }});
        card.addView(stop, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)) {{ topMargin = Ui.dp(MainActivity.this, 8); }});
        return card;
    }

    private TextView sectionTitle(String title) {
        TextView tv = Ui.text(this, title, 10, 0xFF8B97A8, true);
        tv.setLetterSpacing(0.18f);
        return tv;
    }

    private TextView statusRow(LinearLayout parent, String name, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.text(this, name, 12, 0xFFB5C0CF, false);
        TextView value = Ui.text(this, status, 11, 0xFF7A8798, true);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1));
        row.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 36)));
        parent.addView(row);
        return value;
    }

    private SwitchCompat switchRow(LinearLayout parent, String label, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = Ui.text(this, label, 12, 0xFFB5C0CF, false);
        row.addView(tv, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(checked);
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 46)));
        parent.addView(row);
        return sw;
    }

    private RadioButton radio(String label, String tag) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextColor(0xFFCAD2DE);
        rb.setTextSize(11);
        rb.setTag(tag);
        rb.setId(View.generateViewId());
        return rb;
    }

    private void addTheme(RadioGroup group, String label, String value) {
        RadioButton rb = radio(label, value);
        group.addView(rb, new RadioGroup.LayoutParams(0, Ui.dp(this, 40), 1));
        if (value.equals(Prefs.get(this).getString(Prefs.THEME, "violet"))) rb.setChecked(true);
        group.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) {
                Prefs.get(this).edit().putString(Prefs.THEME, String.valueOf(r.getTag())).apply();
                restartIfRunning();
            }
        });
    }

    private void updateStatuses() {
        if (overlayStatus != null) {
            boolean granted = Settings.canDrawOverlays(this);
            overlayStatus.setText(granted ? "GRANTED" : "REQUIRED");
            overlayStatus.setTextColor(granted ? 0xFF78E5B0 : 0xFFFF7F96);
        }
        if (runningStatus != null) {
            boolean running = Prefs.overlayEnabled(this);
            runningStatus.setText(running ? "ACTIVE" : "STOPPED");
            runningStatus.setTextColor(running ? 0xFF78E5B0 : 0xFF8A95A6);
        }
        if (shizukuStatus != null) {
            boolean ok = ShizukuBridge.available();
            shizukuStatus.setText(ok ? "READY" : "NOT READY");
            shizukuStatus.setTextColor(ok ? 0xFF78E5B0 : 0xFFFFC36B);
        }
    }

    private void startEdge() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlay();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, EdgeFloatService.class));
        else startService(new Intent(this, EdgeFloatService.class));
        Prefs.setOverlayEnabled(this, true);
        Toast.makeText(this, "FloatSpace edge aktif", Toast.LENGTH_SHORT).show();
        updateStatuses();
    }

    private void stopEdge() {
        stopService(new Intent(this, EdgeFloatService.class));
        Prefs.setOverlayEnabled(this, false);
        Toast.makeText(this, "FloatSpace edge dihentikan", Toast.LENGTH_SHORT).show();
        updateStatuses();
    }

    private void restartIfRunning() {
        if (Prefs.overlayEnabled(this) && Settings.canDrawOverlays(this)) {
            stopService(new Intent(this, EdgeFloatService.class));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, EdgeFloatService.class));
            else startService(new Intent(this, EdgeFloatService.class));
        }
    }

    private void requestOverlay() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void requestShizuku() {
        if (ShizukuBridge.available()) {
            Toast.makeText(this, "Shizuku sudah siap", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Shizuku.pingBinder()) {
            openShizuku();
            Toast.makeText(this, "Start Shizuku dulu, lalu kembali ke FloatSpace", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
                if (requestCode == REQUEST_SHIZUKU) updateStatuses();
            });
            Shizuku.requestPermission(REQUEST_SHIZUKU);
        } catch (Throwable e) {
            Toast.makeText(this, "Tidak bisa meminta izin Shizuku", Toast.LENGTH_SHORT).show();
        }
    }

    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) startActivity(launch);
        else Toast.makeText(this, "Shizuku belum terpasang", Toast.LENGTH_SHORT).show();
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatuses();
    }

    private static final class BackdropView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public BackdropView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            p.setShader(new RadialGradient(w * .78f, h * .1f, Math.max(w, h) * .45f,
                    new int[]{0x553F2A6A, 0x1111192A, 0x0007090D},
                    new float[]{0f, .46f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * .1f, h * .7f, Math.max(w, h) * .36f,
                    new int[]{0x22435C77, 0x0007090D}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
    }
}
