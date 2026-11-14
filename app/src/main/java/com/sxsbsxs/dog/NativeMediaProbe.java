package com.sxsbsxs.dog;

/** JNI bridge for the native C++ media signature probe. */
public final class NativeMediaProbe {
    static {
        System.loadLibrary("dogmedia");
    }

    private NativeMediaProbe() {}

    /** Returns a MIME type inferred from the file signature, or an empty string. */
    public static native String detectMimeType(String absolutePath);
}
