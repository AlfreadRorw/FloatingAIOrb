package com.alfread.floatspace.model;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppRepository {
    private final Context context;
    private final SharedPreferences prefs;

    public AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("apps", Context.MODE_PRIVATE);
    }

    public List<AppInfo> getApps() {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        ArrayList<AppInfo> result = new ArrayList<>();
        String own = context.getPackageName();
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || own.equals(ai.packageName)) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            result.add(new AppInfo(label, ai.packageName, ai.name, ri.loadIcon(pm)));
        }
        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase()));
        return result;
    }

    public Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet("favorites", Collections.emptySet()));
    }

    public boolean isFavorite(String pkg) {
        return favorites().contains(pkg);
    }

    public void toggleFavorite(String pkg) {
        Set<String> set = favorites();
        if (!set.add(pkg)) set.remove(pkg);
        prefs.edit().putStringSet("favorites", set).apply();
    }
}
