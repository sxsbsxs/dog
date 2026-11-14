package com.sxsbsxs.dog;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Java-side Android storage adapter. It publishes complete files to the public
 * Downloads collection using MediaStore, without broad storage permissions.
 */
public final class MediaStorePublisher {
    private static final String RELATIVE_DIRECTORY =
            Environment.DIRECTORY_DOWNLOADS + "/Dog/";

    private MediaStorePublisher() {}

    /**
     * Publishes one completed file. On Android 9 and older, keeps the file in
     * the application's private download directory and returns its path.
     */
    public static String publish(Context context, File source) throws IOException {
        if (source == null || !source.isFile() || source.length() <= 0L) {
            throw new IOException("Downloaded file is missing or empty");
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return source.getAbsolutePath();
        }

        ContentResolver resolver = context.getContentResolver();
        String displayName = uniqueDisplayName(resolver, source.getName());

        int dot = displayName.lastIndexOf('.');
        String extension = dot >= 0 ? displayName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

        // C++ JNI file-signature detection is a fallback only. Known extensions
        // remain authoritative (for example, M4A should not become video/mp4).
        if (mimeType == null || mimeType.isEmpty()) {
            String detectedMimeType = NativeMediaProbe.detectMimeType(source.getAbsolutePath());
            mimeType = detectedMimeType == null || detectedMimeType.isEmpty()
                    ? "application/octet-stream"
                    : detectedMimeType;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIRECTORY);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (itemUri == null) {
            throw new IOException("Unable to create a Downloads media entry");
        }

        boolean published = false;
        try {
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = resolver.openOutputStream(itemUri, "w")) {
                if (output == null) {
                    throw new IOException("Unable to open Downloads output stream");
                }
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }

            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(itemUri, ready, null, null);
            published = true;
            return itemUri.toString();
        } finally {
            if (!published) {
                resolver.delete(itemUri, null, null);
            }
        }
    }

    private static String uniqueDisplayName(ContentResolver resolver, String original) {
        String candidate = original;
        int dot = original.lastIndexOf('.');
        String base = dot > 0 ? original.substring(0, dot) : original;
        String extension = dot > 0 ? original.substring(dot) : "";
        int suffix = 2;

        while (exists(resolver, candidate)) {
            candidate = base + " (" + suffix + ")" + extension;
            suffix++;
        }
        return candidate;
    }

    private static boolean exists(ContentResolver resolver, String name) {
        String[] projection = { MediaStore.MediaColumns._ID };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] arguments = { name, RELATIVE_DIRECTORY };
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, arguments, null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }
}
