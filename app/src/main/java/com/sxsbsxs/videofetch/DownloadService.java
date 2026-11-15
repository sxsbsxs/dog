package com.sxsbsxs.videofetch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * Java owns the complete download lifecycle: request construction, Cookie
 * injection, native downloader selection, progress notifications, cancellation
 * and publication into Android's public Downloads collection.
 */
public class DownloadService extends Service {
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_FORMAT_SELECTOR = "extra_format_selector";
    public static final String EXTRA_AUDIO_ONLY = "extra_audio_only";
    public static final String EXTRA_FORMAT_LABEL = "extra_format_label";
    public static final String ACTION_CANCEL = "com.sxsbsxs.videofetch.CANCEL_DOWNLOAD";
    public static final String ACTION_CLEAR_QUEUE = "com.sxsbsxs.videofetch.CLEAR_DOWNLOAD_QUEUE";

    private static final String TAG = "VideoFetchDownload";
    private static final String CHANNEL_ID = "videofetch_downloads";
    private static final int NOTIFICATION_ID = 7421;
    private static final int CANCEL_REQUEST_ID = 7422;
    private static final String PROCESS_ID = "videofetch-download-process";
    private static final java.util.regex.Pattern DOWNLOAD_PROGRESS_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%");
    private static final java.util.regex.Pattern ARIA2_PROGRESS_PATTERN =
            java.util.regex.Pattern.compile("\\[#\\w{6}.*?\\((\\d+(?:\\.\\d+)?)%\\)");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object queueLock = new Object();
    private static final class DownloadTask {
        final String url;
        final String formatSelector;
        final boolean audioOnly;
        final String formatLabel;
        final String videoContainer;
        final String audioFormat;
        final String subtitleLanguages;
        final boolean downloadSubtitles;
        final boolean downloadAutoSubtitles;
        final boolean embedSubtitles;
        final boolean downloadPlaylist;
        final boolean embedThumbnail;
        final boolean writeThumbnail;
        final boolean embedMetadata;
        final boolean useAria2;
        final boolean skipExisting;
        final int concurrentFragments;
        final int maxHeight;
        final int retries;
        final String audioQuality;
        final String rateLimit;
        final String filenameTemplate;
        final String destinationTreeUri;

        DownloadTask(
                String url,
                String formatSelector,
                boolean audioOnly,
                String formatLabel,
                android.content.SharedPreferences preferences
        ) {
            this.url = url;
            this.formatSelector = formatSelector == null ? "" : formatSelector;
            this.audioOnly = audioOnly;
            this.formatLabel = formatLabel == null ? "" : formatLabel;
            this.videoContainer = preferences.getString("video_container", "mp4");
            this.audioFormat = preferences.getString("audio_format", "mp3");
            this.subtitleLanguages = preferences.getString("subtitle_languages", "all");
            this.downloadSubtitles = preferences.getBoolean("download_subtitles", false);
            this.downloadAutoSubtitles = preferences.getBoolean("download_auto_subtitles", false);
            this.embedSubtitles = preferences.getBoolean("embed_subtitles", true);
            this.downloadPlaylist = preferences.getBoolean("download_playlist", false);
            this.embedThumbnail = preferences.getBoolean("embed_thumbnail", true);
            this.writeThumbnail = preferences.getBoolean("write_thumbnail", false);
            this.embedMetadata = preferences.getBoolean("embed_metadata", true);
            this.useAria2 = preferences.getBoolean("use_aria2", true);
            this.skipExisting = preferences.getBoolean("skip_existing", false);
            this.concurrentFragments = preferences.getInt("concurrent_fragments", 4);
            this.maxHeight = preferences.getInt("max_height", 0);
            this.retries = preferences.getInt("download_retries", 10);
            this.audioQuality = preferences.getString("audio_quality", "0");
            this.rateLimit = preferences.getString("rate_limit", "");
            this.filenameTemplate = preferences.getString("filename_template", "title");
            this.destinationTreeUri = preferences.getString("download_tree_uri", "");
        }
    }

    private final Queue<DownloadTask> pendingTasks = new ArrayDeque<>();
    private volatile Future<?> downloadFuture;
    private volatile int latestStartId;
    private volatile int activeGeneration;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelDownload();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_CLEAR_QUEUE.equals(intent.getAction())) {
            synchronized (queueLock) {
                latestStartId = startId;
                pendingTasks.clear();
                if (running.get()) {
                    DownloadState.INSTANCE.queueCleared();
                    NotificationManager manager = getSystemService(NotificationManager.class);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID,
                                buildNotification("等待队列已清空 · 当前下载继续", 0, true));
                    }
                } else {
                    stopSelfResult(startId);
                }
            }
            return START_NOT_STICKY;
        }

        String url = intent == null ? null : intent.getStringExtra(EXTRA_URL);
        if (!isHttpUrl(url)) {
            if (!running.get()) stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        String formatSelector = intent.getStringExtra(EXTRA_FORMAT_SELECTOR);
        boolean audioOnly = intent.getBooleanExtra(
                EXTRA_AUDIO_ONLY,
                getSharedPreferences("videofetch_settings", Context.MODE_PRIVATE).getBoolean("audio_only", false)
        );
        String formatLabel = intent.getStringExtra(EXTRA_FORMAT_LABEL);
        android.content.SharedPreferences taskPreferences =
                getSharedPreferences("videofetch_settings", Context.MODE_PRIVATE);
        DownloadTask task = new DownloadTask(
                url, formatSelector, audioOnly, formatLabel, taskPreferences);

        synchronized (queueLock) {
            latestStartId = startId;
            if (running.get()) {
                pendingTasks.offer(task);
                int count = pendingTasks.size();
                DownloadState.INSTANCE.queued(count, pendingTaskLabels());
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID,
                            buildNotification("已加入队列 · 前面还有 " + count + " 个任务", 0, true));
                }
                return START_NOT_STICKY;
            }

            running.set(true);
            int generation = ++activeGeneration;
            createNotificationChannel();
            String preparing = task.formatLabel.isEmpty()
                    ? "正在准备下载" : "正在准备下载 · " + task.formatLabel;
            startForeground(NOTIFICATION_ID, buildNotification(preparing, 0, true));
            DownloadState.INSTANCE.started(
                    pendingTasks.size(), task.formatLabel, task.url, task.formatSelector,
                    task.audioOnly, pendingTaskLabels());
            downloadFuture = executor.submit(() -> runDownload(task, generation));
        }
        return START_NOT_STICKY;
    }

    /** Returns a stable display-only snapshot; raw source URLs are never exposed in task UI. */
    private List<String> pendingTaskLabels() {
        List<String> labels = new ArrayList<>();
        for (DownloadTask task : pendingTasks) {
            String label = task.formatLabel;
            if (label == null || label.trim().isEmpty()) {
                label = task.audioOnly ? "仅音频 · 自动选择最佳" : "视频 · 自动选择最佳";
            }
            labels.add(label);
        }
        return labels;
    }

    private boolean isHttpUrl(String value) {
        return value != null &&
                (value.regionMatches(true, 0, "https://", 0, 8) ||
                 value.regionMatches(true, 0, "http://", 0, 7));
    }

    private void runDownload(DownloadTask task, int generation) {
        final String url = task.url;
        File temporaryCookieFile = null;
        final StringBuilder processOutputTail = new StringBuilder();
        try {
            File outputDirectory = getDownloadDirectory();
            Map<String, String> before = snapshotFiles(outputDirectory);
            YtDlpEngine.initialize(getApplicationContext());

            YoutubeDLRequest request = buildRequest(url, outputDirectory, task);

            // Keep the decrypted jar in app-private cache only for this yt-dlp process.
            temporaryCookieFile = SecureCookieStore.createTemporaryCookieFile(getApplicationContext());
            applyCookieOptions(request, temporaryCookieFile);

            if (generation != activeGeneration || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Download canceled before starting");
            }

            final int[] lastProgress = {-1};
            Function3<Float, Long, String, Unit> progressCallback = (progress, eta, outputLine) -> {
                // Capture the tail because redirecting stderr into stdout is required
                // for progress parsing, and the dependency then leaves stderr empty.
                if (outputLine != null && !outputLine.isEmpty()) {
                    synchronized (processOutputTail) {
                        processOutputTail.append(outputLine).append('\n');
                        if (processOutputTail.length() > 6000) {
                            processOutputTail.delete(0, processOutputTail.length() - 4000);
                        }
                    }
                }

                // StreamProcessExtractor requires both a percent and ETA in a
                // strict MM:SS form. yt-dlp may emit an unknown ETA; parse the
                // percent independently so the UI never stays stuck at 0%.
                int parsedProgress = resolveProgress(progress, outputLine);
                if (parsedProgress < 0) {
                    if (lastProgress[0] < 0) {
                        DownloadState.INSTANCE.updateMessage("已连接下载源，正在获取实时进度…");
                    }
                    return Unit.INSTANCE;
                }

                int percent = Math.max(lastProgress[0], Math.min(100, parsedProgress));
                lastProgress[0] = percent;
                String speed = extractSpeed(outputLine);
                String message = "正在下载 · " + percent + "%" +
                        (speed.isEmpty() ? "" : " · " + speed) +
                        (eta != null && eta > 0 ? " · 剩余约 " + eta + " 秒" : "");

                DownloadState.INSTANCE.updateProgress(percent, message);
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, buildNotification(message, percent, true));
                }
                return Unit.INSTANCE;
            };

            // 0.18.1's 4-argument overload merges stderr into stdout. yt-dlp
            // progress output can be written to stderr; without this flag the
            // upstream StreamProcessExtractor never sees those lines.
            executeWithYouTubeFallback(
                    url, outputDirectory, task, temporaryCookieFile,
                    request, progressCallback, processOutputTail
            );
            if (generation != activeGeneration || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Download canceled after extraction");
            }

            List<File> completedFiles = findCompletedFiles(outputDirectory, before);
            if (completedFiles.isEmpty()) {
                boolean archiveEnabled = task.skipExisting;
                if (archiveEnabled) {
                    String message = "没有新文件：视频可能都已下载过，已按下载归档跳过";
                    DownloadHistoryStore.record(
                            this, url, "", "", "completed", message, task.formatLabel);
                    DownloadState.INSTANCE.completed("", message);
                    NotificationManager manager = getSystemService(NotificationManager.class);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, buildNotification(message, 100, false));
                    }
                    return;
                }
                throw new IOException("下载任务已结束，但没有找到新的视频或音频文件");
            }

            int publishedCount = 0;
            int publishFailures = 0;
            String selectedTree = task.destinationTreeUri;
            boolean customFolder = selectedTree != null && !selectedTree.trim().isEmpty();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || customFolder) {
                for (File file : completedFiles) {
                    try {
                        String publishedUri = customFolder
                                ? MediaStorePublisher.publishToTree(this, file, Uri.parse(selectedTree))
                                : MediaStorePublisher.publish(this, file);
                        publishedCount++;
                        DownloadHistoryStore.record(
                                this, url, file.getName(), publishedUri, "completed", "下载完成", task.formatLabel);
                        if (!file.delete()) {
                            Log.w(TAG, "Published file but could not remove staging copy: " + file);
                        }
                    } catch (Exception publishFailure) {
                        publishFailures++;
                        DownloadHistoryStore.record(
                                this, url, file.getName(), file.getAbsolutePath(),
                                "saved_private", "目标目录保存失败，文件仍保留在应用目录", task.formatLabel);
                        Log.e(TAG, "Could not publish " + file.getName(), publishFailure);
                    }
                }
            } else {
                publishedCount = completedFiles.size();
                for (File file : completedFiles) {
                    DownloadHistoryStore.record(
                            this, url, file.getName(), file.getAbsolutePath(),
                            "completed", "下载完成", task.formatLabel);
                }
            }

            String location;
            String finishedMessage;
            if (publishedCount > 0 && customFolder) {
                location = "已保存到你选择的文件夹（" + publishedCount + " 个文件）";
                finishedMessage = publishFailures == 0
                        ? "下载完成，已保存到你选择的文件夹"
                        : "下载完成，" + publishedCount + " 个文件已保存，" + publishFailures + " 个保留在应用目录";
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && publishedCount > 0) {
                location = "公共下载目录：Download/video-dl（" + publishedCount + " 个文件）";
                if (publishFailures > 0) {
                    location += "；未发布文件暂存于：" + outputDirectory.getAbsolutePath();
                }
                finishedMessage = publishFailures == 0
                        ? "下载完成，已保存到 Download/video-dl"
                        : "下载完成，" + publishedCount + " 个文件已保存，" + publishFailures + " 个保留在应用目录";
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                location = outputDirectory.getAbsolutePath();
                finishedMessage = "下载完成，但无法发布到目标目录；文件暂存在应用目录";
            } else {
                location = outputDirectory.getAbsolutePath();
                finishedMessage = "下载完成，已保存到系统下载目录";
            }

            DownloadState.INSTANCE.completed(location, finishedMessage);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(finishedMessage, 100, false));
            }
        } catch (YoutubeDL.CanceledException canceled) {
            DownloadHistoryStore.record(this, url, "", "", "canceled", "已取消下载", task.formatLabel);
            DownloadState.INSTANCE.canceled();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            DownloadHistoryStore.record(this, url, "", "", "canceled", "已取消下载", task.formatLabel);
            DownloadState.INSTANCE.canceled();
        } catch (Throwable failure) {
            String diagnostic;
            synchronized (processOutputTail) {
                diagnostic = processOutputTail.toString();
            }
            String message = formatDownloadError(failure, diagnostic);
            String safeDiagnostic = buildSafeDiagnostic(failure, diagnostic);
            DownloadHistoryStore.record(this, url, "", "", "failed", message, task.formatLabel);
            DownloadState.INSTANCE.failed(message, safeDiagnostic);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(message, 0, false));
            }
            Log.e(TAG, message, failure);
        } finally {
            if (temporaryCookieFile != null && temporaryCookieFile.exists() && !temporaryCookieFile.delete()) {
                Log.w(TAG, "Could not remove temporary plaintext cookie file");
            }

            int completedStartId = -1;
            synchronized (queueLock) {
                // Ignore completion callbacks from a task invalidated by Cancel.
                if (generation == activeGeneration) {
                    DownloadTask nextTask = pendingTasks.poll();
                    if (nextTask != null) {
                        int remaining = pendingTasks.size();
                        DownloadState.INSTANCE.started(
                                remaining, nextTask.formatLabel, nextTask.url,
                                nextTask.formatSelector, nextTask.audioOnly, pendingTaskLabels());
                        NotificationManager manager = getSystemService(NotificationManager.class);
                        if (manager != null) {
                            manager.notify(NOTIFICATION_ID,
                                    buildNotification("正在准备下一个下载任务", 0, true));
                        }
                        downloadFuture = executor.submit(() -> runDownload(nextTask, generation));
                    } else {
                        running.set(false);
                        stopForeground(STOP_FOREGROUND_DETACH);
                        // Capture this generation's latest start ID. If another
                        // URL arrives immediately after leaving the lock,
                        // stopSelfResult(oldId) must not stop that new request.
                        completedStartId = latestStartId;
                    }
                }
            }
            if (completedStartId >= 0) {
                stopSelfResult(completedStartId);
            }
        }
    }

    private int resolveProgress(Float nativeProgress, String outputLine) {
        // Prefer the current raw line. The upstream extractor can retain its
        // previous percentage when a later line has an unknown ETA.
        if (outputLine != null && !outputLine.isEmpty()) {
            java.util.regex.Matcher download = DOWNLOAD_PROGRESS_PATTERN.matcher(outputLine);
            if (download.find()) {
                try {
                    return Math.min(100, Math.max(0,
                            (int) Math.round(Double.parseDouble(download.group(1)))));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }

            java.util.regex.Matcher aria2 = ARIA2_PROGRESS_PATTERN.matcher(outputLine);
            if (aria2.find()) {
                try {
                    return Math.min(100, Math.max(0,
                            (int) Math.round(Double.parseDouble(aria2.group(1)))));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        if (nativeProgress != null && Float.isFinite(nativeProgress) && nativeProgress >= 0f) {
            return Math.min(100, Math.round(nativeProgress));
        }
        return -1;
    }

    private String extractSpeed(String outputLine) {
        if (outputLine == null || outputLine.isEmpty()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)\\bat\\s+([^\\s]+/s)")
                .matcher(outputLine);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String collectFailureDetails(Throwable failure) {
        StringBuilder details = new StringBuilder();
        appendFailureDetails(details, failure, "error", 0);
        return details.toString();
    }

    private void appendFailureDetails(
            StringBuilder details, Throwable failure, String label, int depth
    ) {
        if (failure == null || depth > 6 || details.length() > 9000) return;
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = failure.getClass().getSimpleName();
        }
        if (details.length() > 0) details.append('\n');
        details.append('[').append(label).append("] ")
                .append(failure.getClass().getSimpleName()).append(": ")
                .append(message.trim());

        Throwable[] suppressed = failure.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            appendFailureDetails(details, suppressed[i], "suppressed-" + (i + 1), depth + 1);
        }
        Throwable cause = failure.getCause();
        if (cause != null && cause != failure) {
            appendFailureDetails(details, cause, "cause", depth + 1);
        }
    }

    private String formatDownloadError(Throwable failure, String processOutputTail) {
        String detail = collectFailureDetails(failure);
        if (detail.trim().isEmpty()) detail = processOutputTail == null ? "" : processOutputTail.trim();
        if (detail.length() > 3600) detail = detail.substring(detail.length() - 3600);
        String normalized = detail.toLowerCase(java.util.Locale.ROOT);
        String friendly;
        String combinedDiagnostics = detail + "\\n" + processOutputTail;
        if (YouTubeRequestFallback.isRetryableFailure(combinedDiagnostics)) {
            friendly = YouTubeRequestFallback.userMessage(combinedDiagnostics);
        } else if (normalized.contains("unsupported url") ||
                normalized.contains("no suitable extractor") ||
                normalized.contains("no extractor")) {
            friendly = "暂不支持这个链接，请复制具体的视频播放页地址";
        } else if (normalized.contains("403") || normalized.contains("forbidden")) {
            friendly = "网站拒绝访问（403），请检查解析引擎版本和网络出口";
        } else if (normalized.contains("sign in") ||
                normalized.contains("login required") ||
                normalized.contains("authentication") ||
                normalized.contains("members-only")) {
            friendly = "这个内容需要账号登录，请到设置中完成网站授权";
        } else if (normalized.contains("no space left") ||
                normalized.contains("not enough space") ||
                normalized.contains("disk full")) {
            friendly = "手机存储空间不足，请清理空间后重试";
        } else if (normalized.contains("ffmpeg") ||
                normalized.contains("postprocessing") ||
                normalized.contains("merger")) {
            friendly = "音视频合并失败，请检查解析引擎和 FFmpeg 运行组件";
        } else if (normalized.contains("timed out") ||
                normalized.contains("timeout") ||
                normalized.contains("connection reset") ||
                normalized.contains("network is unreachable") ||
                normalized.contains("unable to download webpage")) {
            friendly = "网络连接失败，请检查网络后重试";
        } else {
            friendly = detail.length() > 180 ? detail.substring(0, 180) + "…" : detail;
        }
        return "下载失败：" + friendly;
    }

    /**
     * Build a support report with engine/runtime metadata and the full fallback
     * chain. Cookie values and signed URL query strings are redacted before it
     * is stored in UI state or copied to the clipboard.
     */
    private String buildSafeDiagnostic(Throwable failure, String processOutputTail) {
        StringBuilder report = new StringBuilder();
        report.append("VideoFetch 诊断信息\\n");
        try {
            String version = YoutubeDL.getInstance().versionName(getApplicationContext());
            if (version == null || version.trim().isEmpty()) {
                version = YoutubeDL.getInstance().version(getApplicationContext());
            }
            report.append("yt-dlp: ")
                    .append(version == null || version.trim().isEmpty() ? "unknown" : version)
                    .append('\\n');
        } catch (Throwable ignored) {
            report.append("yt-dlp: unknown\\n");
        }

        File quickJs = new File(getApplicationInfo().nativeLibraryDir, "libqjs.so");
        report.append("QuickJS: ").append(quickJs.isFile() ? "present" : "missing").append('\\n');
        try {
            String jar = SecureCookieStore.loadCookieJar(getApplicationContext());
            report.append("Cookie jar: ")
                    .append(CookieJarExporter.countCookies(jar)).append(" rows / ")
                    .append(CookieJarExporter.countDomains(jar)).append(" domains; values hidden\\n");
        } catch (Throwable ignored) {
            report.append("Cookie jar: unavailable\\n");
        }

        String nested = collectFailureDetails(failure);
        if (!nested.trim().isEmpty()) {
            report.append("\\n--- yt-dlp failure chain ---\\n").append(nested);
        }
        if (processOutputTail != null && !processOutputTail.trim().isEmpty()) {
            report.append("\\n--- process output tail ---\\n").append(processOutputTail.trim());
        }

        String safe = report.toString()
                .replaceAll("(?im)(cookie|set-cookie)\\\\s*:\\\\s*[^\\\\r\\\\n]+", "$1: [已隐藏]")
                .replaceAll("(?i)(--cookies\\\\s+)[^\\\\s]+", "$1[已隐藏]")
                .replaceAll("(?i)\\\\b(SID|HSID|SSID|SAPISID|APISID|LOGIN_INFO|LSID|NID|__Host-[A-Za-z0-9_-]+|__Secure-[A-Za-z0-9_-]+)\\\\s*=\\\\s*[^\\\\s;]+", "$1=[已隐藏]")
                .replaceAll("https?://[^\\\\s\\\\\"<>]*\\\\?[^\\\\s\\\\\"<>]*", "[含查询参数的 URL 已隐藏]");
        if (safe.length() > 4800) safe = safe.substring(safe.length() - 4800);
        return safe;
    }

    private void applyCookieOptions(YoutubeDLRequest request, File temporaryCookieFile) {
        if (temporaryCookieFile != null) {
            request.addOption("--cookies", temporaryCookieFile.getAbsolutePath());
            return;
        }

        // Compatibility with Cookie headers saved by older VideoFetch versions.
        // Never send an unrestricted legacy header when a scoped cookie jar exists.
        String legacyCookie = SecureCookieStore.load(getApplicationContext());
        if (legacyCookie != null) {
            legacyCookie = legacyCookie.trim();
            if (legacyCookie.regionMatches(true, 0, "Cookie:", 0, 7)) {
                legacyCookie = legacyCookie.substring(7).trim();
            }
        }
        if (legacyCookie != null && !legacyCookie.isEmpty()) {
            request.addOption("--add-header", "Cookie: " + legacyCookie);
        }
    }

    private void executeWithYouTubeFallback(
            String url,
            File outputDirectory,
            DownloadTask task,
            File temporaryCookieFile,
            YoutubeDLRequest primaryRequest,
            Function3<Float, Long, String, Unit> progressCallback,
            StringBuilder processOutputTail
    ) throws Exception {
        try {
            YoutubeDL.getInstance().execute(primaryRequest, PROCESS_ID, true, progressCallback);
            return;
        } catch (Exception firstFailure) {
            String firstDiagnostics;
            synchronized (processOutputTail) {
                firstDiagnostics = processOutputTail.toString();
            }
            if (!YouTubeRequestFallback.shouldRetry(url, firstFailure, firstDiagnostics)) {
                throw firstFailure;
            }

            DownloadState.INSTANCE.updateMessage("YouTube 验证受限，正在尝试 TV 客户端…");
            YoutubeDLRequest tvRequest = buildRequest(url, outputDirectory, task, false, true);
            applyCookieOptions(tvRequest, temporaryCookieFile);
            YouTubeRequestFallback.applyFallback(tvRequest);
            try {
                YoutubeDL.getInstance().execute(tvRequest, PROCESS_ID, true, progressCallback);
                return;
            } catch (Exception tvFailure) {
                DownloadState.INSTANCE.updateMessage("TV 客户端仍不可用，正在尝试 Safari HLS 流…");
                YoutubeDLRequest safariRequest = buildRequest(url, outputDirectory, task, false, true);
                applyCookieOptions(safariRequest, temporaryCookieFile);
                YouTubeRequestFallback.applyHlsFallback(safariRequest);
                try {
                    YoutubeDL.getInstance().execute(safariRequest, PROCESS_ID, true, progressCallback);
                } catch (Exception safariFailure) {
                    safariFailure.addSuppressed(tvFailure);
                    safariFailure.addSuppressed(firstFailure);
                    throw safariFailure;
                }
            }
        }
    }

    private YoutubeDLRequest buildRequest(String url, File directory, DownloadTask task) throws IOException {
        return buildRequest(url, directory, task, true, false);
    }

    /**
     * Fallback requests use yt-dlp's native downloader. In particular, Safari
     * may expose HLS formats that must not be forced through aria2c.
     * Their format IDs can differ from the primary client, so retry with a
     * fresh best-available selector rather than reusing a stale selected ID.
     */
    private YoutubeDLRequest buildRequest(
            String url, File directory, DownloadTask task,
            boolean allowAria2, boolean useFallbackFormat
    ) throws IOException {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--plugin-dirs",
                YtDlpEngine.getPluginDirectory(getApplicationContext()).getAbsolutePath());
        if (YouTubeRequestFallback.isYoutubeUrl(url)) {
            YouTubeRequestFallback.applyCommonOptions(request);
        }
        String outputTemplate;
        if ("title_id".equals(task.filenameTemplate)) {
            outputTemplate = "%(title).180B [%(id)s].%(ext)s";
        } else if ("id_title".equals(task.filenameTemplate)) {
            outputTemplate = "%(id)s %(title).180B.%(ext)s";
        } else {
            outputTemplate = "%(title).200B.%(ext)s";
        }
        request.addOption("-o", new File(directory, outputTemplate).getAbsolutePath());
        request.addOption("--newline");
        request.addOption("--progress");
        // Emit a stable yt-dlp progress line. youtubedl-android parses
        // "[download] 12.3% ... ETA 00:12" from stdout for its callback.
        request.addOption("--progress-template",
                "download:[download] %(progress._percent_str)s of %(progress._total_bytes_str)s at %(progress._speed_str)s ETA %(progress._eta_str)s");
        request.addOption("--progress-delta", "1");
        request.addOption("--no-colors");
        request.addOption("--no-mtime");
        int retryCount = Math.max(1, Math.min(30, task.retries));
        request.addOption("--retries", Integer.toString(retryCount));
        request.addOption("--fragment-retries", Integer.toString(retryCount));
        request.addOption("--file-access-retries", "3");
        if (task.embedMetadata) request.addOption("--embed-metadata");
        if (task.embedThumbnail) request.addOption("--embed-thumbnail");
        if (task.writeThumbnail) request.addOption("--write-thumbnail");
        String rateLimit = task.rateLimit == null ? "" : task.rateLimit.trim();
        if (rateLimit.matches("\\d+(?:\\.\\d+)?[KMGkmg]?")) {
            request.addOption("--limit-rate", rateLimit);
        }

        boolean downloadSubtitles = task.downloadSubtitles;
        boolean downloadPlaylist = task.downloadPlaylist;
        int concurrentFragments = Math.max(1, Math.min(8, task.concurrentFragments));
        request.addOption("--concurrent-fragments", Integer.toString(concurrentFragments));
        if (task.skipExisting) {
            request.addOption("--download-archive", new File(getFilesDir(), "download-archive.txt").getAbsolutePath());
        }
        request.addOption(downloadPlaylist ? "--yes-playlist" : "--no-playlist");
        if (downloadPlaylist) request.addOption("--no-abort-on-error");
        if (task.audioOnly) {
            String audioSelector = useFallbackFormat ? "" : task.formatSelector.trim();
            if (!audioSelector.isEmpty()) {
                if (!isSafeFormatSelector(audioSelector) || audioSelector.contains("+")) {
                    throw new IllegalArgumentException("无效的音频格式 ID");
                }
                request.addOption("-f", audioSelector);
            } else {
                request.addOption("-f", "ba/b");
            }
            request.addOption("-x");
            String audioFormat = task.audioFormat;
            if (!"mp3".equals(audioFormat) && !"m4a".equals(audioFormat) &&
                    !"opus".equals(audioFormat) && !"flac".equals(audioFormat)) {
                audioFormat = "mp3";
            }
            request.addOption("--audio-format", audioFormat);
            String audioQuality = task.audioQuality == null ? "0" : task.audioQuality;
            if (!audioQuality.matches("(?:0|[1-9]|10)")) audioQuality = "0";
            request.addOption("--audio-quality", audioQuality);
        } else {
            String format = useFallbackFormat ? "" : task.formatSelector.trim();
            if (!format.isEmpty()) {
                if (!isSafeFormatSelector(format)) {
                    throw new IllegalArgumentException("无效的视频/音频格式 ID");
                }
            } else {
                int maxHeight = task.maxHeight;
                format = maxHeight > 0 && !useFallbackFormat
                        ? "bv*[height=" + maxHeight + "]+ba/b[height=" + maxHeight + "]"
                        : maxHeight > 0
                                ? "bv*[height<=" + maxHeight + "]+ba/b[height<=" + maxHeight + "]"
                                : "bv*+ba/b";
            }
            request.addOption("-f", format);
            String videoContainer = task.videoContainer;
            if (!"mp4".equals(videoContainer) && !"mkv".equals(videoContainer) &&
                    !"webm".equals(videoContainer)) {
                videoContainer = "mp4";
            }
            request.addOption("--merge-output-format", videoContainer);
        }

        boolean downloadAutoSubtitles = task.downloadAutoSubtitles;
        if (downloadSubtitles) request.addOption("--write-subs");
        if (downloadAutoSubtitles) request.addOption("--write-auto-subs");
        if (downloadSubtitles || downloadAutoSubtitles) {
            String subtitleLanguages = task.subtitleLanguages;
            if (subtitleLanguages == null || subtitleLanguages.trim().isEmpty()) {
                subtitleLanguages = "all";
            }
            request.addOption("--sub-langs", subtitleLanguages.trim());
            if (!task.audioOnly && task.embedSubtitles) request.addOption("--embed-subs");
        }

        // aria2c is optional. Leave it disabled on fallback clients because
        // web_safari can return HLS playlists handled by yt-dlp's native path.
        if (allowAria2 && task.useAria2) {
            request.addOption("--downloader", "libaria2c.so");
        }
        return request;
    }

    private boolean isSafeFormatSelector(String selector) {
        return selector != null && selector.matches(
                "[A-Za-z0-9._-]{1,80}(?:\\+[A-Za-z0-9._-]{1,80})?"
        );
    }

    private File getDownloadDirectory() throws IOException {
        File base;
        String selectedTree = getSharedPreferences(
                "videofetch_settings", Context.MODE_PRIVATE).getString("download_tree_uri", "");
        boolean customFolder = selectedTree != null && !selectedTree.trim().isEmpty();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !customFolder) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("需要存储权限才能写入系统下载目录");
            }
            base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        } else {
            base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (base == null) base = getFilesDir();
        }

        File directory = new File(
                base,
                customFolder ? "video-dl-staging"
                        : Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ? "video-dl" : "VideoFetch"
        );
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建下载目录：" + directory);
        }
        return directory;
    }

    private Map<String, String> snapshotFiles(File directory) {
        Map<String, String> result = new HashMap<>();
        collectFileStamps(directory, result, 0);
        return result;
    }

    private void collectFileStamps(File directory, Map<String, String> result, int depth) {
        if (directory == null || depth > 12) return;
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectFileStamps(file, result, depth + 1);
            } else if (file.isFile() && file.length() > 0L && !isTemporaryFile(file)) {
                result.put(file.getAbsolutePath(), file.length() + ":" + file.lastModified());
            }
        }
    }

    private List<File> findCompletedFiles(File directory, Map<String, String> before) {
        List<File> result = new ArrayList<>();
        collectCompletedFiles(directory, before, result, 0);
        return result;
    }

    private void collectCompletedFiles(
            File directory, Map<String, String> before, List<File> result, int depth) {
        if (directory == null || depth > 12) return;
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectCompletedFiles(file, before, result, depth + 1);
                continue;
            }
            if (!file.isFile() || file.length() <= 0L || isTemporaryFile(file)) continue;

            String path = file.getAbsolutePath();
            String current = file.length() + ":" + file.lastModified();
            if (!current.equals(before.get(path))) result.add(file);
        }
    }

    private boolean isTemporaryFile(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".part") || name.endsWith(".ytdl") ||
                name.endsWith(".temp") || name.endsWith(".tmp") ||
                name.endsWith(".aria2") || name.endsWith(".lock");
    }

    private void cancelDownload() {
        synchronized (queueLock) {
            pendingTasks.clear();
            activeGeneration++;
            try {
                YoutubeDL.getInstance().destroyProcessById(PROCESS_ID);
            } catch (Throwable ignored) {
                // The process may have exited before the cancel action arrived.
            }

            Future<?> future = downloadFuture;
            if (future != null) future.cancel(true);
            running.set(false);
            DownloadState.INSTANCE.canceled();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message, int progress, boolean ongoing) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("VideoFetch 下载器")
                .setContentText(message)
                .setContentIntent(openPendingIntent)
                .setProgress(100, Math.max(0, Math.min(100, progress)), ongoing && progress == 0)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing);

        if (ongoing) {
            Intent cancelIntent = new Intent(this, DownloadService.class).setAction(ACTION_CANCEL);
            PendingIntent cancelPendingIntent = PendingIntent.getService(
                    this, CANCEL_REQUEST_ID, cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPendingIntent);
        }
        return builder.build();
    }

    private void removeNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override
    public void onDestroy() {
        if (running.get()) cancelDownload();
        executor.shutdownNow();
        super.onDestroy();
    }

    public static void start(Context context, String url) {
        boolean audioOnly = context.getSharedPreferences(
                "videofetch_settings", Context.MODE_PRIVATE).getBoolean("audio_only", false);
        start(context, url, "", audioOnly);
    }

    public static void start(Context context, String url, String formatSelector, boolean audioOnly) {
        start(context, url, formatSelector, audioOnly, "");
    }

    public static void start(
            Context context, String url, String formatSelector, boolean audioOnly, String formatLabel) {
        Intent intent = new Intent(context, DownloadService.class)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FORMAT_SELECTOR, formatSelector == null ? "" : formatSelector)
                .putExtra(EXTRA_AUDIO_ONLY, audioOnly)
                .putExtra(EXTRA_FORMAT_LABEL, formatLabel == null ? "" : formatLabel);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_CANCEL);
        context.startService(intent);
    }

    /** Clears only pending tasks. The currently running download is left untouched. */
    public static void clearQueue(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_CLEAR_QUEUE);
        context.startService(intent);
    }
}
