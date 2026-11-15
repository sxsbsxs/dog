package com.sxsbsxs.videofetch;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tiny Java-only crash recorder. It intentionally has no Compose, Python,
 * yt-dlp, FFmpeg, or JNI dependencies so it can run during early startup.
 */
public final class CrashReporter {
    private static final String REPORT_NAME = "video-dl-last-crash.txt";
    private static final String PREFS = "video_dl_diagnostics";
    private static final String STAGE_KEY = "startup_stage";

    private CrashReporter() {}

    public static void install(Application application) {
        final Context app = application.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            try {
                writeCrash(app, thread, failure);
            } catch (Throwable ignored) {
                // Never replace the original app crash with a logging failure.
            }
            if (previous != null) {
                previous.uncaughtException(thread, failure);
            }
        });
        mark(app, "Application.onCreate");
    }

    public static void mark(Context context, String stage) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(STAGE_KEY, stage).apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean hasPreviousCrash(Context context) {
        return reportFile(context).isFile();
    }

    public static String readPreviousCrash(Context context) {
        File file = reportFile(context);
        if (!file.isFile()) return "";
        try (java.io.FileInputStream input = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            return "无法读取上次闪退记录：" + failure;
        }
    }

    public static void clearPreviousCrash(Context context) {
        File file = reportFile(context);
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private static File reportFile(Context context) {
        return new File(context.getFilesDir(), REPORT_NAME);
    }

    private static void writeCrash(Context context, Thread thread, Throwable failure)
            throws Exception {
        String stage = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(STAGE_KEY, "unknown");
        String version = "unknown";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            version = info.versionName == null ? "unknown" : info.versionName;
        } catch (Throwable ignored) {
        }

        StringBuilder report = new StringBuilder(8192);
        report.append("video-dl crash report\n")
                .append("time: ").append(new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(new Date())).append('\n')
                .append("package: ").append(context.getPackageName()).append('\n')
                .append("version: ").append(version).append('\n')
                .append("android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n')
                .append("abi: ").append(Build.SUPPORTED_ABIS.length > 0
                        ? Build.SUPPORTED_ABIS[0] : "unknown").append('\n')
                .append("startup stage: ").append(stage).append('\n')
                .append("thread: ").append(thread == null ? "unknown" : thread.getName())
                .append("\n\n")
                .append(android.util.Log.getStackTraceString(failure));

        File target = reportFile(context);
        File temp = new File(context.getFilesDir(), REPORT_NAME + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(report.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Cannot replace previous crash report");
        }
        if (!temp.renameTo(target)) {
            throw new IllegalStateException("Cannot finalize crash report");
        }
    }
}
