package com.alfread.arachne;

import android.content.Context;
import android.content.SharedPreferences;

final class WidgetPrefs {
    private static final String PREF = "arachne_widget_prefs";
    static final String THEME = "theme";
    static final String STYLE = "style";
    static final String TWELVE = "twelve";
    static final String SECONDS = "seconds";
    static final String DATE = "date";
    static final String LABEL = "label";
    static final String DIM = "dim";
    static final String CUSTOM = "custom";

    private WidgetPrefs() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String key(int id, String key) { return id + "_" + key; }

    static String getTheme(Context c, int id) { return prefs(c).getString(key(id, THEME), "gold"); }
    static String getStyle(Context c, int id) { return prefs(c).getString(key(id, STYLE), "arachne"); }
    static boolean isTwelve(Context c, int id) { return prefs(c).getBoolean(key(id, TWELVE), false); }
    static boolean showSeconds(Context c, int id) { return prefs(c).getBoolean(key(id, SECONDS), true); }
    static boolean showDate(Context c, int id) { return prefs(c).getBoolean(key(id, DATE), true); }
    static boolean showLabel(Context c, int id) { return prefs(c).getBoolean(key(id, LABEL), true); }
    static boolean isDim(Context c, int id) { return prefs(c).getBoolean(key(id, DIM), false); }
    static String getCustom(Context c, int id) { return prefs(c).getString(key(id, CUSTOM), ""); }

    static void save(Context c, int id, String theme, String style, boolean twelve,
                     boolean seconds, boolean date, boolean label, boolean dim, String custom) {
        prefs(c).edit()
                .putString(key(id, THEME), theme)
                .putString(key(id, STYLE), style)
                .putBoolean(key(id, TWELVE), twelve)
                .putBoolean(key(id, SECONDS), seconds)
                .putBoolean(key(id, DATE), date)
                .putBoolean(key(id, LABEL), label)
                .putBoolean(key(id, DIM), dim)
                .putString(key(id, CUSTOM), custom == null ? "" : custom)
                .apply();
    }

    static void remove(Context c, int id) {
        prefs(c).edit()
                .remove(key(id, THEME)).remove(key(id, STYLE))
                .remove(key(id, TWELVE)).remove(key(id, SECONDS))
                .remove(key(id, DATE)).remove(key(id, LABEL)).remove(key(id, DIM)).remove(key(id, CUSTOM))
                .apply();
    }
}
