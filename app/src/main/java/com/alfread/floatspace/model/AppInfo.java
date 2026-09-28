package com.alfread.floatspace.model;

import android.graphics.drawable.Drawable;

public final class AppInfo {
    public final String label;
    public final String packageName;
    public final String activityName;
    public final Drawable icon;

    public AppInfo(String label, String packageName, String activityName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
    }
}
