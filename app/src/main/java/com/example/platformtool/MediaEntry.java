package com.example.platformtool;

import android.net.Uri;

final class MediaEntry {
    final Uri uri;
    final String name;

    MediaEntry(Uri uri, String name) {
        this.uri = uri;
        this.name = name == null || name.trim().isEmpty() ? "未命名文件" : name;
    }
}
