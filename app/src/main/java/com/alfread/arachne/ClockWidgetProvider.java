package com.alfread.arachne;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

public class ClockWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_UPDATE = "com.alfread.arachne.UPDATE_WIDGET";

    private static final int GOLD = Color.rgb(232, 198, 106);
    private static final int VIOLET = Color.rgb(184, 125, 255);
    private static final int CYAN = Color.rgb(76, 225, 255);
    private static final int CRIMSON = Color.rgb(255, 86, 112);
    private static final int EMERALD = Color.rgb(70, 224, 154);
    private static final int ICE = Color.rgb(194, 221, 255);

    static RemoteViews buildViews(Context context, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
        String theme = WidgetPrefs.getTheme(context, id);
        String custom = WidgetPrefs.getCustom(context, id);
        String style = WidgetPrefs.getStyle(context, id);
        boolean twelve = WidgetPrefs.isTwelve(context, id);
        boolean seconds = WidgetPrefs.showSeconds(context, id);
        boolean date = WidgetPrefs.showDate(context, id);
        boolean label = WidgetPrefs.showLabel(context, id);
        boolean dim = WidgetPrefs.isDim(context, id);
        int accent = accent(theme);
        try { if (custom != null && custom.matches("#[0-9a-fA-F]{6}")) accent = Color.parseColor(custom); } catch (Exception ignored) {}

        // TextClock is the key to true launcher-side realtime ticking.
        v.setCharSequence(R.id.widgetTime, "setFormat24Hour", "HH:mm");
        v.setCharSequence(R.id.widgetTime, "setFormat12Hour", "h:mm");
        v.setCharSequence(R.id.widgetSeconds, "setFormat24Hour", "ss");
        v.setCharSequence(R.id.widgetSeconds, "setFormat12Hour", "ss");
        v.setViewVisibility(R.id.widgetSeconds, seconds ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.widgetDate, date ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.widgetLabel, label ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.widgetCore, "arachne".equals(style) ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.widgetHud, "terminal".equals(style) ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.widgetRing, "minimal".equals(style) ? android.view.View.GONE : android.view.View.VISIBLE);
        v.setViewVisibility(R.id.widgetBrand, label ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.widgetLive, dim ? android.view.View.GONE : android.view.View.VISIBLE);
        v.setViewVisibility(R.id.widgetSecondMark, seconds ? android.view.View.VISIBLE : android.view.View.GONE);

        v.setTextColor(R.id.widgetTime, accent);
        v.setTextColor(R.id.widgetBrand, accent);
        v.setTextColor(R.id.widgetLive, dim ? Color.rgb(55, 57, 65) : blend(accent, Color.WHITE, .15f));
        v.setTextColor(R.id.widgetSecondMark, dim ? Color.rgb(55, 57, 65) : Color.rgb(98, 102, 116));
        v.setTextColor(R.id.widgetSeconds, dim ? blend(accent, Color.BLACK, .45f) : accent);
        v.setTextColor(R.id.widgetDate, dim ? Color.rgb(90, 92, 100) : Color.rgb(155, 158, 170));
        v.setTextColor(R.id.widgetLabel, dim ? Color.rgb(68, 70, 77) : Color.rgb(108, 111, 121));
        v.setTextColor(R.id.widgetHud, dim ? Color.rgb(68, 70, 77) : accent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setColorStateList(R.id.widgetCore, "setImageTintList", android.content.res.ColorStateList.valueOf(accent));
        }
        // RemoteViews tinting is available through setColorStateList on API 31+.
        // The old setImageViewTintList call is not part of the public RemoteViews API
        // used by this project and causes javac to fail.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setColorStateList(R.id.widgetRing, "setImageTintList",
                    android.content.res.ColorStateList.valueOf(accent));
        }

        int bg = background(style, dim);
        v.setInt(R.id.widgetRoot, "setBackgroundColor", bg);
        v.setTextViewText(R.id.widgetHud, style.equals("terminal") ? "LOCAL / SYSTEM  •  LIVE" : "");

        if (twelve) {
            v.setCharSequence(R.id.widgetTime, "setFormat24Hour", "h:mm");
            v.setCharSequence(R.id.widgetTime, "setFormat12Hour", "h:mm");
        }

        Intent launch = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, id, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widgetRoot, pi);
        return v;
    }

    private static int accent(String theme) {
        switch (theme) {
            case "violet": return VIOLET;
            case "cyan": return CYAN;
            case "crimson": return CRIMSON;
            case "emerald": return EMERALD;
            case "ice": return ICE;
            default: return GOLD;
        }
    }

    private static int background(String style, boolean dim) {
        if (dim) return Color.rgb(3, 4, 7);
        if ("minimal".equals(style)) return Color.rgb(9, 10, 14);
        if ("terminal".equals(style)) return Color.rgb(4, 10, 12);
        return Color.rgb(8, 9, 13);
    }

    private static int blend(int fg, int bg, float amount) {
        return Color.rgb(
                (int)(Color.red(fg) * (1f - amount) + Color.red(bg) * amount),
                (int)(Color.green(fg) * (1f - amount) + Color.green(bg) * amount),
                (int)(Color.blue(fg) * (1f - amount) + Color.blue(bg) * amount));
    }

    private static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, ClockWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(provider)) {
            manager.updateAppWidget(id, buildViews(context, id));
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, buildViews(context, id));
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetPrefs.remove(context, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_UPDATE.equals(intent.getAction())) updateAll(context);
    }
}
