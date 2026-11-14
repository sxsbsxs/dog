package com.sxsbsxs.dog;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Small local download history. It deliberately stores only the source host,
 * not the full URL, so signed links and query tokens are not retained.
 */
public final class DownloadHistoryStore {
    private static final String PREFS = "dog_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 100;

    private DownloadHistoryStore() {}

    public static synchronized void record(
            Context context,
            String sourceUrl,
            String fileName,
            String location,
            String status,
            String message
    ) {
        try {
            SharedPreferences preferences = preferences(context);
            JSONArray previous = new JSONArray(preferences.getString(KEY_ITEMS, "[]"));
            JSONArray next = new JSONArray();

            JSONObject item = new JSONObject();
            item.put("timestamp", System.currentTimeMillis());
            item.put("source", sourceHost(sourceUrl));
            item.put("fileName", fileName == null ? "" : fileName);
            item.put("location", location == null ? "" : location);
            item.put("status", status == null ? "unknown" : status);
            item.put("message", message == null ? "" : message);
            next.put(item);

            for (int i = 0; i < previous.length() && i < MAX_ITEMS - 1; i++) {
                JSONObject old = previous.optJSONObject(i);
                if (old != null) next.put(old);
            }
            preferences.edit().putString(KEY_ITEMS, next.toString()).apply();
        } catch (Exception ignored) {
            // Download completion must never fail because history storage is unavailable.
        }
    }

    public static synchronized String read(Context context) {
        return preferences(context).getString(KEY_ITEMS, "[]");
    }

    public static synchronized void clear(Context context) {
        preferences(context).edit().remove(KEY_ITEMS).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String sourceHost(String sourceUrl) {
        try {
            String host = Uri.parse(sourceUrl).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }
}
