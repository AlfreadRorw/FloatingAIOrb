package com.alfread.statusvault;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class HistoryStore {
    private static final String PREF = "history";
    private static final String KEY = "items";

    private final SharedPreferences prefs;

    public HistoryStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized List<HistoryItem> load() {
        List<HistoryItem> result = new ArrayList<>();
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                result.add(new HistoryItem(
                        o.optString("name"),
                        o.optString("destination"),
                        o.optLong("size"),
                        o.optLong("timestamp"),
                        o.optString("mode"),
                        o.optString("result")
                ));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public synchronized void add(HistoryItem item) {
        List<HistoryItem> items = load();
        items.add(0, item);
        while (items.size() > 150) items.remove(items.size() - 1);
        save(items);
    }

    public synchronized void clear() {
        prefs.edit().remove(KEY).apply();
    }

    private void save(List<HistoryItem> items) {
        JSONArray array = new JSONArray();
        try {
            for (HistoryItem item : items) {
                JSONObject o = new JSONObject();
                o.put("name", item.name);
                o.put("destination", item.destination);
                o.put("size", item.size);
                o.put("timestamp", item.timestamp);
                o.put("mode", item.mode);
                o.put("result", item.result);
                array.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString(KEY, array.toString()).apply();
    }
}
