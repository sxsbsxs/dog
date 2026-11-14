package com.sxsbsxs.dog;

import android.content.Context;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Updates only the yt-dlp extractor script distributed by the upstream runtime. */
public final class EngineUpdateManager {
    public interface Callback {
        void onComplete(String message);
    }

    private EngineUpdateManager() {}

    public static void update(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String result;
            try {
                YtDlpEngine.initialize(app);
                YoutubeDL.UpdateStatus status = YoutubeDL.getInstance().updateYoutubeDL(
                        app, YoutubeDL.UpdateChannel._STABLE);
                String version = YoutubeDL.getInstance().versionName(app);
                if (status == YoutubeDL.UpdateStatus.DONE) {
                    result = "yt-dlp 已更新" + (version == null ? "" : "：" + version);
                } else {
                    result = "yt-dlp 已是最新版本" + (version == null ? "" : "：" + version);
                }
            } catch (Throwable failure) {
                String detail = failure.getMessage();
                if (detail == null || detail.trim().isEmpty()) detail = failure.getClass().getSimpleName();
                if (detail.length() > 180) detail = detail.substring(0, 180) + "…";
                result = "解析引擎更新失败：" + detail;
            }

            final String message = result;
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> callback.onComplete(message));
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onComplete(message));
            }
            executor.shutdown();
        });
    }
}
