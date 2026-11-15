package com.sxsbsxs.videofetch;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Java-side Android storage adapter. It publishes complete files to the public
 * Downloads collection using MediaStore, without broad storage permissions.
 */
public final class MediaStorePublisher {
    private static final String RELATIVE_DIRECTORY =
            Environment.DIRECTORY_DOWNLOADS + "/video-dl/";

    private MediaStorePublisher() {}

    /**
     * Publishes one completed file. The C implementation calculates a source
     * fingerprint; Java recalculates it during the copy and rejects corruption.
     */
    public static String publish(Context context, File source) throws IOException {
        if (source == null || !source.isFile() || source.length() <= 0L) {
            throw new IOException("Downloaded file is missing or empty");
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return source.getAbsolutePath();
        }

        String expectedFingerprint = NativeMediaProbe.calculateFingerprint(source.getAbsolutePath());
        if (expectedFingerprint == null || expectedFingerprint.isEmpty()) {
            throw new IOException("Native C checksum could not read the downloaded file");
        }

        ContentResolver resolver = context.getContentResolver();
        String displayName = uniqueDisplayName(resolver, source.getName());

        int dot = displayName.lastIndexOf('.');
        String extension = dot >= 0 ? displayName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
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
            CRC32 copiedChecksum = new CRC32();
            long copiedBytes = 0L;

            try (InputStream input = new FileInputStream(source);
                 OutputStream output = resolver.openOutputStream(itemUri, "w")) {
                if (output == null) {
                    throw new IOException("Unable to open Downloads output stream");
                }
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    copiedChecksum.update(buffer, 0, count);
                    copiedBytes += count;
                }
                output.flush();
            }

            String actualFingerprint = copiedBytes + ":" +
                    String.format(Locale.ROOT, "%08x", copiedChecksum.getValue());
            if (!expectedFingerprint.equals(actualFingerprint)) {
                throw new IOException("Media copy integrity check failed");
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

    /**
     * Publishes a finished download into a folder selected through Android's
     * Storage Access Framework. The tree permission is persisted by MainActivity.
     */
    public static String publishToTree(Context context, File source, Uri treeUri) throws IOException {
        if (source == null || !source.isFile() || source.length() <= 0L) {
            throw new IOException("Downloaded file is missing or empty");
        }
        if (treeUri == null) throw new IOException("No destination folder was selected");

        DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);
        if (tree == null || !tree.isDirectory() || !tree.canWrite()) {
            throw new IOException("所选下载目录不可写，请重新选择文件夹");
        }

        String originalName = source.getName();
        String displayName = originalName;
        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        String extension = dot > 0 ? originalName.substring(dot) : "";
        int suffix = 2;
        while (tree.findFile(displayName) != null) {
            displayName = base + " (" + suffix + ")" + extension;
            suffix++;
        }

        String extensionOnly = dot > 0
                ? originalName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOnly);
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = NativeMediaProbe.detectMimeType(source.getAbsolutePath());
        }
        if (mimeType == null || mimeType.isEmpty()) mimeType = "application/octet-stream";

        DocumentFile destination = tree.createFile(mimeType, displayName);
        if (destination == null) throw new IOException("无法在所选目录中创建文件");

        boolean published = false;
        try {
            String expectedFingerprint = NativeMediaProbe.calculateFingerprint(source.getAbsolutePath());
            if (expectedFingerprint == null || expectedFingerprint.isEmpty()) {
                throw new IOException("无法校验下载文件");
            }

            CRC32 copiedChecksum = new CRC32();
            long copiedBytes = 0L;
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = context.getContentResolver()
                         .openOutputStream(destination.getUri(), "w")) {
                if (output == null) throw new IOException("无法写入所选下载目录");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    copiedChecksum.update(buffer, 0, count);
                    copiedBytes += count;
                }
                output.flush();
            }

            String actualFingerprint = copiedBytes + ":" +
                    String.format(Locale.ROOT, "%08x", copiedChecksum.getValue());
            if (!expectedFingerprint.equals(actualFingerprint)) {
                throw new IOException("保存到所选目录时文件校验失败");
            }

            published = true;
            return destination.getUri().toString();
        } finally {
            if (!published) destination.delete();
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
