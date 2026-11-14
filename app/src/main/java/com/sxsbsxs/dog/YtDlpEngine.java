package com.sxsbsxs.dog;

import android.content.Context;

import com.yausername.aria2c.Aria2c;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

/**
 * Initializes the device-local media runtime.
 *
 * CPython hosts yt-dlp. FFmpeg and aria2c are native Android packages.
 */
public final class YtDlpEngine {
    private static volatile boolean initialized;

    private YtDlpEngine() {}

    public static synchronized void initialize(Context context) throws Exception {
        if (initialized) return;

        Context appContext = context.getApplicationContext();
        YoutubeDL.init(appContext);
        FFmpeg.init(appContext);
        Aria2c.init(appContext);
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
