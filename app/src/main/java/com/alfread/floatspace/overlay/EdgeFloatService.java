package com.alfread.floatspace.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.alfread.floatspace.MainActivity;
import com.alfread.floatspace.util.Prefs;
import com.alfread.floatspace.util.Ui;

public class EdgeFloatService extends Service {
    private static final int NOTIFICATION_ID = 4112;
    private WindowManager wm;
    private Context overlayContext;
    private View edgeView;
    private WindowManager.LayoutParams edgeParams;
    private AppDrawerOverlay drawer;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        setupOverlayContext();
        showEdgeHandle();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void setupOverlayContext() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.hardware.display.DisplayManager dm = getSystemService(android.hardware.display.DisplayManager.class);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            overlayContext = createDisplayContext(d).createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            wm = (WindowManager) overlayContext.getSystemService(WINDOW_SERVICE);
        } else {
            overlayContext = this;
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
    }

    private void showEdgeHandle() {
        if (edgeView != null) return;
        boolean left = "left".equals(Prefs.get(this).getString(Prefs.EDGE_SIDE, "right"));
        int length = Prefs.get(this).getInt(Prefs.EDGE_LENGTH, 170);
        int accent = accentForTheme(Prefs.get(this).getString(Prefs.THEME, "violet"));
        edgeView = new EdgeHandleView(overlayContext, left, accent);
        edgeView.setOnClickListener(v -> {
            if (Prefs.get(this).getBoolean(Prefs.EDGE_HAPTIC, true))
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            showDrawer();
        });
        int width = Ui.dp(overlayContext, 8);
        edgeParams = new WindowManager.LayoutParams(
                width,
                Ui.dp(overlayContext, length),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        edgeParams.gravity = (left ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
        edgeParams.x = Ui.dp(overlayContext, 2);
        edgeParams.y = 0;
        try {
            wm.addView(edgeView, edgeParams);
        } catch (Exception e) {
            Toast.makeText(this, "FloatSpace: overlay gagal", Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    public void showDrawer() {
        if (drawer != null) return;
        drawer = new AppDrawerOverlay(this, wm, overlayContext, () -> {
            if (drawer != null) {
                drawer.dismiss();
                drawer = null;
            }
        });
        drawer.show();
    }

    @Override
    public void onDestroy() {
        try {
            if (drawer != null) drawer.dismiss();
            if (edgeView != null) wm.removeViewImmediate(edgeView);
        } catch (Exception ignored) {}
        edgeView = null;
        drawer = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    "floatspace_overlay", "FloatSpace overlay", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the floating edge handle available.");
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, EdgeFloatService.class);
        stop.setAction("STOP");
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, "floatspace_overlay")
                .setSmallIcon(com.alfread.floatspace.R.drawable.ic_launcher)
                .setContentTitle("FloatSpace active")
                .setContentText("Tap the edge handle to open apps and controls")
                .setOngoing(true)
                .setContentIntent(pi)
                .addAction(new NotificationCompat.Action(0, "Stop", stopPi))
                .build();
    }

    private int accentForTheme(String theme) {
        switch (theme) {
            case "cyan": return 0xFF66E6FF;
            case "gold": return 0xFFFFC86B;
            case "ice": return 0xFFC7DAFF;
            default: return 0xFFB58CFF;
        }
    }

}
