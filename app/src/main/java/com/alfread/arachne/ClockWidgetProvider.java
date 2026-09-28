package com.alfread.arachne;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ClockWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_UPDATE = "com.alfread.arachne.UPDATE_WIDGET";

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
        Date now = new Date();
        views.setTextViewText(R.id.widgetTime, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        views.setTextViewText(R.id.widgetSeconds, new SimpleDateFormat("ss", Locale.getDefault()).format(now));

        Intent launch = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, id, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widgetRoot, pi);
        manager.updateAppWidget(id, views);
    }

    private static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, ClockWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(provider)) updateOne(context, manager, id);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_UPDATE.equals(intent.getAction())) updateAll(context);
    }
}
