package com.example.platformtool;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persists SAF media URIs so playlists survive process death and app restarts. */
final class MediaLibraryStore {
    private static final String PREFS_NAME = "media_library";
    private static final String KEY_AUDIO = "audio_entries";
    private static final String KEY_VIDEO = "video_entries";

    private static boolean audioLoaded;
    private static boolean videoLoaded;

    private MediaLibraryStore() { }

    static synchronized void ensureLoaded(Context context, List<MediaEntry> target,
                                          boolean audio) {
        if (audio ? audioLoaded : videoLoaded) return;
        String stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(audio ? KEY_AUDIO : KEY_VIDEO, null);
        if (stored != null && !stored.isEmpty()) {
            List<MediaEntry> restored = decode(stored);
            target.clear();
            target.addAll(restored);
        }
        if (audio) audioLoaded = true; else videoLoaded = true;
    }

    static synchronized void save(Context context, List<MediaEntry> source, boolean audio) {
        JSONArray array = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (MediaEntry entry : new ArrayList<>(source)) {
            String uri = entry.uri.toString();
            if (!seen.add(uri)) continue;
            JSONObject object = new JSONObject();
            try {
                object.put("uri", uri);
                object.put("name", entry.name);
                array.put(object);
            } catch (JSONException ignored) {
                // String values are JSON-safe; skip an entry if a vendor implementation fails.
            }
        }
        SharedPreferences.Editor editor = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(audio ? KEY_AUDIO : KEY_VIDEO, array.toString()).apply();
    }

    private static List<MediaEntry> decode(String stored) {
        List<MediaEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            JSONArray array = new JSONArray(stored);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) continue;
                String uri = object.optString("uri", "");
                if (uri.isEmpty() || !seen.add(uri)) continue;
                entries.add(new MediaEntry(Uri.parse(uri), object.optString("name", null)));
            }
        } catch (JSONException ignored) {
            // A damaged cache must not prevent either player from opening.
        }
        return entries;
    }
}
