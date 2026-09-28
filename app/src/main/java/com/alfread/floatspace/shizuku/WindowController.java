package com.alfread.floatspace.shizuku;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.alfread.floatspace.model.AppInfo;
import com.alfread.floatspace.util.Ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WindowController {
    private static final int FREEFORM = 5;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, WindowState> STATES = new ConcurrentHashMap<>();
    private static volatile String lastPackage;

    private WindowController() {}

    public static void launch(Context context, AppInfo app, boolean forceResizable) {
        Context appContext = context.getApplicationContext();
        EXEC.execute(() -> {
            if (!ShizukuBridge.available()) {
                MAIN.post(() -> {
                    try {
                        Intent i = new Intent();
                        i.setClassName(app.packageName, app.activityName);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        appContext.startActivity(i);
                        toast(appContext, "Shizuku belum aktif: aplikasi dibuka normal");
                    } catch (Exception e) {
                        toast(appContext, "Gagal membuka " + app.label);
                    }
                });
                return;
            }

            if (forceResizable) {
                ShizukuBridge.exec("settings", "put", "global", "enable_freeform_support", "1");
                ShizukuBridge.exec("settings", "put", "secure", "force_resizable_activities", "1");
            }

            Rect bounds = initialBounds(appContext);
            String component = app.packageName + "/" + app.activityName;
            String boundArg = bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom;
            ShizukuBridge.CommandResult result = ShizukuBridge.exec(
                    "am", "start", "--user", "0", "--windowingMode", String.valueOf(FREEFORM),
                    "--bounds", boundArg, "-n", component
            );
            if (!result.ok()) {
                result = ShizukuBridge.exec(
                        "am", "start", "--user", "0", "--windowingMode", String.valueOf(FREEFORM),
                        "-n", component
                );
            }

            WindowState state = new WindowState(app.packageName, bounds);
            STATES.put(app.packageName, state);
            lastPackage = app.packageName;
            if (!result.ok()) {
                final String error = result.err;
                MAIN.post(() -> toast(appContext, "Freeform ditolak perangkat: " + safe(error)));
            }
        });
    }

    public static void close(Context context, String packageName) {
        Context appContext = context.getApplicationContext();
        EXEC.execute(() -> {
            ShizukuBridge.CommandResult r = ShizukuBridge.exec("am", "force-stop", packageName);
            STATES.remove(packageName);
            if (packageName.equals(lastPackage)) lastPackage = null;
            if (!r.ok()) {
                MAIN.post(() -> toast(appContext, "Tidak bisa menutup jendela"));
            }
        });
    }

    public static void focus(Context context, String packageName) {
        Context appContext = context.getApplicationContext();
        EXEC.execute(() -> {
            WindowState state = STATES.get(packageName);
            if (state == null) return;
            String taskId = findTaskId(packageName);
            if (taskId == null) return;
            ShizukuBridge.exec("am", "task", "focus", taskId);
        });
    }

    public static void resize(Context context, String packageName, float scale) {
        Context appContext = context.getApplicationContext();
        EXEC.execute(() -> {
            WindowState state = STATES.get(packageName);
            if (state == null) return;
            Rect r = scaleAroundCenter(state.bounds, scale, screenWidth(appContext), screenHeight(appContext));
            applyResize(packageName, state, r, appContext);
        });
    }

    public static void move(Context context, String packageName, int dx, int dy) {
        Context appContext = context.getApplicationContext();
        EXEC.execute(() -> {
            WindowState state = STATES.get(packageName);
            if (state == null) return;
            Rect r = new Rect(state.bounds);
            int w = r.width();
            int h = r.height();
            int maxW = screenWidth(appContext);
            int maxH = screenHeight(appContext);
            int left = clamp(r.left + dx, 0, Math.max(0, maxW - w));
            int top = clamp(r.top + dy, 0, Math.max(0, maxH - h));
            r.set(left, top, left + w, top + h);
            applyResize(packageName, state, r, appContext);
        });
    }

    public static WindowState last() {
        return lastPackage == null ? null : STATES.get(lastPackage);
    }

    private static void applyResize(String packageName, WindowState state, Rect rect, Context context) {
        String taskId = findTaskId(packageName);
        if (taskId == null) return;
        ShizukuBridge.CommandResult result = ShizukuBridge.exec(
                "am", "task", "resize", taskId,
                String.valueOf(rect.left), String.valueOf(rect.top),
                String.valueOf(rect.right), String.valueOf(rect.bottom)
        );
        if (result.ok()) {
            synchronized (state) { state.bounds.set(rect); }
        }
    }

    private static String findTaskId(String packageName) {
        ShizukuBridge.CommandResult r = ShizukuBridge.exec("dumpsys", "activity", "activities");
        if (!r.ok()) return null;
        String pkg = packageName;
        String[] lines = r.out.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            int t = trimmed.indexOf("Task{");
            if (t < 0) continue;
            int p = trimmed.indexOf("#", t);
            int space = trimmed.indexOf(' ', p + 1);
            if (p > 0) {
                String id = trimmed.substring(p + 1, space > p ? space : trimmed.length()).replaceAll("[^0-9]", "");
                if (trimmed.contains("A=" + pkg) || trimmed.contains(" " + pkg + "/") || trimmed.contains(" " + pkg + " ")) {
                    if (!id.isEmpty()) return id;
                }
            }
        }
        // Fallback: parse the resumed/foreground activity line, whose task token is usually tNN.
        for (String line : lines) {
            if (!line.contains(pkg) || !line.contains(" t")) continue;
            int ti = line.indexOf(" t");
            int start = ti + 2;
            int end = start;
            while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
            if (end > start) return line.substring(start, end);
        }
        return null;
    }

    private static Rect initialBounds(Context c) {
        int sw = screenWidth(c);
        int sh = screenHeight(c);
        int w = Math.min(sw - Ui.dp(c, 24), Math.round(sw * 0.72f));
        int h = Math.min(sh - Ui.dp(c, 120), Math.round(sh * 0.58f));
        int left = Math.max(0, (sw - w) / 2);
        int top = Math.max(Ui.dp(c, 48), (sh - h) / 2);
        return new Rect(left, top, Math.min(sw, left + w), Math.min(sh, top + h));
    }

    private static Rect scaleAroundCenter(Rect source, float scale, int maxW, int maxH) {
        int nw = Math.max(260, Math.round(source.width() * scale));
        int nh = Math.max(260, Math.round(source.height() * scale));
        int cx = source.centerX();
        int cy = source.centerY();
        int left = clamp(cx - nw / 2, 0, Math.max(0, maxW - nw));
        int top = clamp(cy - nh / 2, 0, Math.max(0, maxH - nh));
        return new Rect(left, top, Math.min(maxW, left + nw), Math.min(maxH, top + nh));
    }

    private static int screenWidth(Context c) {
        android.view.WindowManager wm = (android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 30) return wm.getMaximumWindowMetrics().getBounds().width();
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return dm.widthPixels;
    }

    private static int screenHeight(Context c) {
        android.view.WindowManager wm = (android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 30) return wm.getMaximumWindowMetrics().getBounds().height();
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return dm.heightPixels;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void toast(Context c, String value) {
        Toast.makeText(c, value, Toast.LENGTH_SHORT).show();
    }

    private static String safe(String s) {
        return s == null || s.isEmpty() ? "unknown error" : s;
    }

    public static final class WindowState {
        public final String packageName;
        public final Rect bounds;
        public WindowState(String packageName, Rect bounds) {
            this.packageName = packageName;
            this.bounds = new Rect(bounds);
        }
    }
}
