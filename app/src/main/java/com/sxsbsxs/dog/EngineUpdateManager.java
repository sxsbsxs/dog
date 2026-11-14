package com.sxsbsxs.dog;

import android.content.Context;
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
