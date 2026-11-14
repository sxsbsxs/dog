package com.sxsbsxs.dog;

import android.content.Context;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches a safe single-item preview before the user starts a download. */
public final class VideoInfoService {
    public interface Callback {
        void onSuccess(Preview preview);
        void onError(String message);
    }

    public static final class Preview {
        public final String title;
        public final String uploader;
        public final String duration;
        public final String resolution;
        public final String fileSize;
        public final String extractor;

        Preview(String title, String uploader, String duration, String resolution,
                String fileSize, String extractor) {
            this.title = title;
            this.uploader = uploader;
            this.duration = duration;
            this.resolution = resolution;
            this.fileSize = fileSize;
            this.extractor = extractor;
        }
    }

    private VideoInfoService() {}

    public static void inspect(Context context, String url, Callback callback) {
        Context app = context.getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            File cookieFile = null;
            try {
                YtDlpEngine.initialize(app);
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--no-playlist");
                request.addOption("--no-warnings");
                cookieFile = SecureCookieStore.createTemporaryCookieFile(app);
                if (cookieFile != null) {
                    request.addOption("--cookies", cookieFile.getAbsolutePath());
                }

                VideoInfo info = YoutubeDL.getInstance().getInfo(request);
                String title = firstNonEmpty(info.getTitle(), info.getFulltitle(), "未知标题");
                String uploader = firstNonEmpty(info.getUploader(), info.getUploaderId(), "");
                String duration = formatDuration(info.getDuration());
                String resolution = firstNonEmpty(info.getResolution(), "");
                if (resolution.isEmpty() && info.getHeight() > 0) {
                    resolution = info.getWidth() > 0
                            ? info.getWidth() + "×" + info.getHeight()
                            : info.getHeight() + "p";
                }
                long bytes = info.getFileSizeApproximate() > 0
                        ? info.getFileSizeApproximate() : info.getFileSize();
                String size = bytes > 0 ? formatSize(bytes) : "未知";
                String extractor = firstNonEmpty(info.getExtractor(), info.getExtractorKey(), "yt-dlp");
                Preview preview = new Preview(title, uploader, duration, resolution, size, extractor);
                callback.onSuccess(preview);
            } catch (Throwable failure) {
                String message = failure.getMessage();
                if (message == null || message.trim().isEmpty()) message = failure.getClass().getSimpleName();
                if (message.length() > 240) message = message.substring(0, 240) + "…";
                callback.onError(message);
            } finally {
                if (cookieFile != null && cookieFile.exists() && !cookieFile.delete()) {
                    cookieFile.deleteOnExit();
                }
                executor.shutdown();
            }
        });
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "时长未知";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.ROOT, "%d:%02d", minutes, remainder);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unit]);
    }
}
