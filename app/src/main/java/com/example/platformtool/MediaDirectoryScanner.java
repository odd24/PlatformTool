package com.example.platformtool;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MediaDirectoryScanner {
    static final int MAX_FILES = 3000;

    private MediaDirectoryScanner() { }

    static List<MediaEntry> scan(ContentResolver resolver, Uri treeUri, boolean audio) {
        List<MediaEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collect(resolver, treeUri, DocumentsContract.getTreeDocumentId(treeUri), audio, result, seen);
        Collections.sort(result, (left, right) -> left.name.compareToIgnoreCase(right.name));
        return result;
    }

    private static void collect(ContentResolver resolver, Uri treeUri, String parentId,
                                boolean audio, List<MediaEntry> result, Set<String> seen) {
        if (result.size() >= MAX_FILES || Thread.currentThread().isInterrupted()) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext() && result.size() < MAX_FILES) {
                String id = cursor.getString(idColumn);
                String name = nameColumn >= 0 ? cursor.getString(nameColumn) : id;
                String mime = mimeColumn >= 0 ? cursor.getString(mimeColumn) : null;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collect(resolver, treeUri, id, audio, result, seen);
                } else if (matches(name, mime, audio)) {
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    if (seen.add(uri.toString())) result.add(new MediaEntry(uri, name));
                }
            }
        } catch (RuntimeException ignored) {
            // Continue when a document provider blocks an individual subdirectory.
        }
    }

    private static boolean matches(String name, String mime, boolean audio) {
        if (mime != null && mime.startsWith(audio ? "audio/" : "video/")) return true;
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (audio) {
            return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac")
                    || lower.endsWith(".m4a") || lower.endsWith(".flac") || lower.endsWith(".ogg")
                    || lower.endsWith(".opus") || lower.endsWith(".amr");
        }
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                || lower.endsWith(".3gp") || lower.endsWith(".avi") || lower.endsWith(".mov")
                || lower.endsWith(".ts") || lower.endsWith(".m4v");
    }
}
