package com.sxsbsxs.dog

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

/**
 * Initializes the Android media toolchain once per process.
 *
 * yt-dlp runs on the bundled Python runtime. FFmpeg and aria2c are native
 * libraries packaged for Android ABIs by the upstream Android runtime.
 */
object YtDlpEngine {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext
        YoutubeDL.init(appContext)
        FFmpeg.init(appContext)
        Aria2c.init(appContext)

        initialized = true
    }
}
