package com.example.platformtool;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MediaFileHelper {
    private MediaFileHelper() { }

    static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (RuntimeException ignored) { }
        return uri.getLastPathSegment() == null ? "媒体文件" : uri.getLastPathSegment();
    }

    static void persistReadPermission(ContentResolver resolver, Uri uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
    }

    static int addIfMissing(List<MediaEntry> target, MediaEntry entry) {
        for (int i = 0; i < target.size(); i++) {
            if (target.get(i).uri.equals(entry.uri)) return i;
        }
        target.add(entry);
        return target.size() - 1;
    }

    static int merge(List<MediaEntry> target, List<MediaEntry> found) {
        Set<String> existing = new HashSet<>();
        for (MediaEntry entry : target) existing.add(entry.uri.toString());
        int added = 0;
        for (MediaEntry entry : found) {
            if (existing.add(entry.uri.toString())) {
                target.add(entry);
                added++;
            }
        }
        return added;
    }
}
