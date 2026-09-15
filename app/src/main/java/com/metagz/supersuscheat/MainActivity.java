package com.metagz.supersuscheat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public static final String CHEAT_FILE = "/sdcard/meta_supersus_cheat.cfg";

    private static final String[] GAME_PKGS = {
        "com.supersus.game",
        "com.supersus.supersus",
        "com.game.supersus",
        "com.supersus.mobile",
        "com.mobilelegends.supersus"
    };

    private TabLayout tabLayout;
    private LinearLayout contentContainer;
    private LinearLayout panelCombat, panelMovement, panelVisual, panelEconomy, panelProfile, panelAbout;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        tabLayout = findViewById(R.id.tabLayout);
        contentContainer = findViewById(R.id.contentContainer);

        panelCombat   = findViewById(R.id.panelCombat);
        panelMovement = findViewById(R.id.panelMovement);
        panelVisual   = findViewById(R.id.panelVisual);
        panelEconomy  = findViewById(R.id.panelEconomy);
        panelProfile  = findViewById(R.id.panelProfile);
        panelAbout    = findViewById(R.id.panelAbout);

        setupTabs();
        requestOverlayPermission();
        bindActions();
        showPanel(0);
    }

    private void setupTabs() {
        String[] tabs = {"COMBAT", "MOVE", "VISUAL", "ECON", "PROFILE", "ABOUT"};
        for (String t : tabs) tabLayout.addTab(tabLayout.newTab().setText(t));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showPanel(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showPanel(int idx) {
        LinearLayout[] panels = {panelCombat, panelMovement, panelVisual, panelEconomy, panelProfile, panelAbout};
        for (int i = 0; i < panels.length; i++) {
            panels[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }
    }

    private void bindActions() {
        // Apply
        Button btnApply = findViewById(R.id.btnApply);
        Button btnLaunch = findViewById(R.id.btnLaunch);
        Button btnFloating = findViewById(R.id.btnFloating);
        TextView tvStatus = findViewById(R.id.tvStatus);

        // Profile buttons
        Button btnSave1 = findViewById(R.id.btnSave1);
        Button btnLoad1 = findViewById(R.id.btnLoad1);
        Button btnReset = findViewById(R.id.btnReset);

        btnApply.setOnClickListener(v -> {
            String cfg = CheatConfig.dumpAll(this);
            try {
                java.io.FileWriter fw = new java.io.FileWriter(CHEAT_FILE, false);
                fw.write(cfg);
                fw.close();
                tvStatus.setText("OK -> " + CHEAT_FILE);
                Toast.makeText(this, "Config saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                tvStatus.setText("ERR: " + e.getMessage());
            }
        });

        btnLaunch.setOnClickListener(v -> {
            startFloatingService();
            launchGame();
        });

        btnFloating.setOnClickListener(v -> startFloatingService());

        btnSave1.setOnClickListener(v -> {
            CheatConfig.saveProfile(this, "default");
            Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
        });

        btnLoad1.setOnClickListener(v -> {
            CheatConfig.loadProfile(this, "default");
            CheatConfig.refreshUI(this);
            Toast.makeText(this, "Profile loaded", Toast.LENGTH_SHORT).show();
        });

        btnReset.setOnClickListener(v -> {
            CheatConfig.resetAll(this);
            CheatConfig.refreshUI(this);
            Toast.makeText(this, "Reset done", Toast.LENGTH_SHORT).show();
        });
    }

    private void startFloatingService() {
        Intent svc = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
    }

    private void launchGame() {
        PackageManager pm = getPackageManager();
        for (String pkg : GAME_PKGS) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                Toast.makeText(this, "Launching: " + pkg, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "Supersus gak ketemu, install dulu", Toast.LENGTH_LONG).show();
    }
}
