package com.sxsbsxs.dog;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.yausername.youtubedl_android.YoutubeDL;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Updates only the yt-dlp extractor script distributed by the upstream runtime. */
public final class EngineUpdateManager {
    public interface Callback {
        void onComplete(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean UPDATING = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private EngineUpdateManager() {}

    public static boolean isUpdating() {
        return UPDATING.get();
    }

    /**
     * Weekly background check inspired by Seal's updater. Runs only on a
     * validated network and never while a download or metadata extraction is active.
     */
    public static boolean updateIfDue(Context context, boolean wifiOnly, Callback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences preferences = app.getSharedPreferences("dog_settings", Context.MODE_PRIVATE);
        if (!preferences.getBoolean("auto_update_engine", true)) return false;
        if (UPDATING.get() || DownloadState.INSTANCE.isRunning() || VideoInfoService.isInspecting()) return false;

        long lastCheck = preferences.getLong("last_engine_update_check", 0L);
        long oneWeek = 7L * 24L * 60L * 60L * 1000L;
        if (System.currentTimeMillis() - lastCheck < oneWeek) return false;

        ConnectivityManager manager =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        if (capabilities == null ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return false;
        }
        if (wifiOnly && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false;

        update(app, message -> {
            if (message != null &&
                    (message.startsWith("yt-dlp 已更新") || message.startsWith("yt-dlp 已是最新版本"))) {
                preferences.edit().putLong("last_engine_update_check", System.currentTimeMillis()).apply();
            }
            if (callback != null) callback.onComplete(message);
        });
        return true;
    }

    public static boolean updateIfDue(Context context, boolean wifiOnly) {
        return updateIfDue(context, wifiOnly, null);
    }

    public static void update(Context context, Callback callback) {
        if (!UPDATING.compareAndSet(false, true)) {
            MAIN.post(() -> callback.onComplete("解析引擎更新已经在进行中"));
            return;
        }

        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            String result;
            try {
                YtDlpEngine.initialize(app);
                YoutubeDL.UpdateStatus status = YoutubeDL.getInstance().updateYoutubeDL(
                        app, YoutubeDL.UpdateChannel._STABLE);
                String version = YoutubeDL.getInstance().versionName(app);
                String suffix = version == null || version.trim().isEmpty() ? "" : "：" + version;
                if (status == YoutubeDL.UpdateStatus.DONE) {
                    result = "yt-dlp 已更新" + suffix;
                } else if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                    result = "yt-dlp 已是最新版本" + suffix;
                } else {
                    result = "解析引擎更新未完成：" + status;
                }
            } catch (Throwable failure) {
                String detail = failure.getMessage();
                if (detail == null || detail.trim().isEmpty()) {
                    detail = failure.getClass().getSimpleName();
                }
                if (detail.length() > 180) detail = detail.substring(0, 180) + "…";
                result = "解析引擎更新失败：" + detail;
            } finally {
                UPDATING.set(false);
            }

            final String message = result;
            MAIN.post(() -> callback.onComplete(message));
        });
    }
}
