package com.sxsbsxs.dog;

/** JNI bridge for the native C/C++ media helpers. */
public final class NativeMediaProbe {
    static {
        System.loadLibrary("dogmedia");
    }

    private NativeMediaProbe() {}

    /** Returns a MIME type inferred from the media file signature, or an empty string. */
    public static native String detectMimeType(String absolutePath);

    /** Returns "byteCount:crc32" for the file, or an empty string on I/O failure. */
    public static native String calculateFingerprint(String absolutePath);
}
