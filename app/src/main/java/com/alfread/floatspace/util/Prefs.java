package com.alfread.floatspace.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "floatspace";
    public static final String EDGE_SIDE = "edge_side";
    public static final String EDGE_LENGTH = "edge_length";
    public static final String EDGE_HAPTIC = "edge_haptic";
    public static final String AUTO_BOOT = "auto_boot";
    public static final String THEME = "theme";
    public static final String FORCE_RESIZABLE = "force_resizable";
    public static final String DIM = "dim";

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean overlayEnabled(Context c) {
        return get(c).getBoolean("overlay_enabled", false);
    }

    public static void setOverlayEnabled(Context c, boolean value) {
        get(c).edit().putBoolean("overlay_enabled", value).apply();
    }
}
