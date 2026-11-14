package com.sxsbsxs.dog

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

/** Initializes the bundled Python + yt-dlp + FFmpeg runtime once per process. */
object YtDlpEngine {
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        YoutubeDL.init(context.applicationContext)
        FFmpeg.init(context.applicationContext)
        initialized = true
    }
}
