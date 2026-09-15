package com.metagz.supersuscheat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private WindowManager wm;
    private View rootView;
    private WindowManager.LayoutParams params;
    private boolean expanded = false;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        inflateOverlay();
    }

    private Notification buildNotification() {
        String ch = "metagz";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(ch, "METAGZ", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
        return new NotificationCompat.Builder(this, ch)
            .setContentTitle("METAGZ Cheat active")
            .setContentText("Floating menu running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build();
    }

    private void inflateOverlay() {
        rootView = LayoutInflater.from(this).inflate(R.layout.floating_menu, null);

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        else
            type = WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 30;
        params.y = 200;

        View header = rootView.findViewById(R.id.floatingHeader);
        LinearLayout body = rootView.findViewById(R.id.floatingBody);
        ImageView icon = rootView.findViewById(R.id.floatingIcon);
        TextView title = rootView.findViewById(R.id.floatingTitle);

        body.setVisibility(View.GONE);

        header.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY;
            float touchX, touchY;
            boolean moved;
            long lastTap;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x; initY = params.y;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - touchX);
                        int dy = (int)(e.getRawY() - touchY);
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true;
                        params.x = initX + dx; params.y = initY + dy;
                        wm.updateViewLayout(rootView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            long now = System.currentTimeMillis();
                            if (now - lastTap < 400) {
                                stopSelf(); // double tap = close
                            } else {
                                expanded = !expanded;
                                body.setVisibility(expanded ? View.VISIBLE : View.GONE);
                                title.setText(expanded ? "METAGZ PANEL" : "METAGZ");
                            }
                            lastTap = now;
                        }
                        return true;
                }
                return false;
            }
        });

        // Bind quick toggles
        bindQuickToggle(rootView, R.id.ftGod, "god_mode");
        bindQuickToggle(rootView, R.id.ftAmmo, "unlimited_ammo");
        bindQuickToggle(rootView, R.id.ftSpeed, "speed_hack");
        bindQuickToggle(rootView, R.id.ftEsp, "esp_players");
        bindQuickToggle(rootView, R.id.ftGold, "unlimited_gold");
        bindQuickToggle(rootView, R.id.ftHeadshot, "auto_headshot");

        wm.addView(rootView, params);
    }

    private void bindQuickToggle(View root, int id, String key) {
        TextView tv = root.findViewById(id);
        if (tv == null) return;
        updateToggleStyle(tv, CheatConfig.isOn(this, key));
        tv.setOnClickListener(v -> {
            boolean now = !CheatConfig.isOn(this, key);
            CheatConfig.setOn(this, key, now);
            updateToggleStyle(tv, now);
        });
    }

    private void updateToggleStyle(TextView tv, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(20);
        g.setColor(on ? Color.parseColor("#00FF88") : Color.parseColor("#33FFFFFF"));
        tv.setBackground(g);
        tv.setTextColor(on ? Color.parseColor("#000000") : Color.parseColor("#FFFFFF"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (rootView != null && wm != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) {}
        }
    }
}
