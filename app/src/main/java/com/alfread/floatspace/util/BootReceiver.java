package com.alfread.floatspace.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.alfread.floatspace.overlay.EdgeFloatService;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Prefs.get(context).getBoolean(Prefs.AUTO_BOOT, false)) return;
        if (!Settings.canDrawOverlays(context)) return;
        Intent service = new Intent(context, EdgeFloatService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception ignored) {
        }
    }
}
