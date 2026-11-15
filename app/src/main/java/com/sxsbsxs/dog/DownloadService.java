package com.sxsbsxs.dog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
    public static final String ACTION_CANCEL = "com.sxsbsxs.dog.CANCEL_DOWNLOAD";
    public static final String ACTION_CLEAR_QUEUE = "com.sxsbsxs.dog.CLEAR_DOWNLOAD_QUEUE";

    private static final String TAG = "DogDownload";
    private static final String CHANNEL_ID = "dog_downloads";
    private static final int NOTIFICATION_ID = 7421;
    private static final int CANCEL_REQUEST_ID = 7422;
    private static final String PROCESS_ID = "dog-download-process";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object queueLock = new Object();
    private static final class DownloadTask {
        final String url;
        final String formatSelector;
        final boolean audioOnly;

        DownloadTask(String url, String formatSelector, boolean audioOnly) {
            this.url = url;
            this.formatSelector = formatSelector == null ? "" : formatSelector;
            this.audioOnly = audioOnly;
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
                getSharedPreferences("dog_settings", Context.MODE_PRIVATE).getBoolean("audio_only", false)
        );
        DownloadTask task = new DownloadTask(url, formatSelector, audioOnly);

        synchronized (queueLock) {
            latestStartId = startId;
            if (running.get()) {
                pendingTasks.offer(task);
                int count = pendingTasks.size();
                DownloadState.INSTANCE.queued(count);
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
            startForeground(NOTIFICATION_ID, buildNotification("正在准备下载", 0, true));
            DownloadState.INSTANCE.started(pendingTasks.size());
            downloadFuture = executor.submit(() -> runDownload(task, generation));
        }
        return START_NOT_STICKY;
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

            YoutubeDLRequest request = buildRequest(
                    url, outputDirectory, task.formatSelector, task.audioOnly);

            // The in-app login WebView exports a complete Netscape cookie jar.
            // Materialize it only for this yt-dlp process; the encrypted source stays private.
            temporaryCookieFile = SecureCookieStore.createTemporaryCookieFile(getApplicationContext());
            if (temporaryCookieFile != null) {
                request.addOption("--cookies", temporaryCookieFile.getAbsolutePath());
            } else {
                // Compatibility with Cookie headers saved by older Dog versions.
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

                // The library reports -1 until its parser recognizes a progress line.
                // Never turn that sentinel into a fake 0% update.
                if (progress == null || !Float.isFinite(progress) || progress < 0f) {
                    if (lastProgress[0] < 0) {
                        DownloadState.INSTANCE.updateMessage("已连接下载源，正在获取实时进度…");
                    }
                    return Unit.INSTANCE;
                }

                int percent = Math.max(lastProgress[0], Math.min(100, Math.round(progress)));
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
            YoutubeDL.getInstance().execute(request, PROCESS_ID, true, progressCallback);
            if (generation != activeGeneration || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Download canceled after extraction");
            }

            List<File> completedFiles = findCompletedFiles(outputDirectory, before);
            if (completedFiles.isEmpty()) {
                boolean archiveEnabled = getSharedPreferences(
                        "dog_settings", Context.MODE_PRIVATE
                ).getBoolean("skip_existing", false);
                if (archiveEnabled) {
                    String message = "没有新文件：视频可能都已下载过，已按下载归档跳过";
                    DownloadHistoryStore.record(
                            this, url, "", "", "completed", message);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (File file : completedFiles) {
                    try {
                        String publishedUri = MediaStorePublisher.publish(this, file);
                        publishedCount++;
                        DownloadHistoryStore.record(
                                this, url, file.getName(), publishedUri, "completed", "下载完成");
                        // Avoid retaining a second full-size copy after public publishing succeeds.
                        if (!file.delete()) {
                            Log.w(TAG, "Published file but could not remove staging copy: " + file);
                        }
                    } catch (Exception publishFailure) {
                        publishFailures++;
                        DownloadHistoryStore.record(
                                this, url, file.getName(), file.getAbsolutePath(),
                                "saved_private", "公共目录发布失败，文件仍保留在应用目录");
                        Log.e(TAG, "Could not publish " + file.getName(), publishFailure);
                    }
                }
            } else {
                publishedCount = completedFiles.size();
                for (File file : completedFiles) {
                    DownloadHistoryStore.record(
                            this, url, file.getName(), file.getAbsolutePath(),
                            "completed", "下载完成");
                }
            }

            String location;
            String finishedMessage;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && publishedCount > 0) {
                location = "公共下载目录：Download/Dog（" + publishedCount + " 个文件）";
                if (publishFailures > 0) {
                    location += "；未发布文件暂存于：" + outputDirectory.getAbsolutePath();
                }
                finishedMessage = publishFailures == 0
                        ? "下载完成，已保存到 Download/Dog"
                        : "下载完成，" + publishedCount + " 个文件已保存，" + publishFailures + " 个保留在应用目录";
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                location = outputDirectory.getAbsolutePath();
                finishedMessage = "下载完成，但无法发布到公共目录；文件暂存在应用目录";
            } else {
                location = outputDirectory.getAbsolutePath();
                finishedMessage = "下载完成，已保存到系统 Download/Dog 目录";
            }

            DownloadState.INSTANCE.completed(location, finishedMessage);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(finishedMessage, 100, false));
            }
        } catch (YoutubeDL.CanceledException canceled) {
            DownloadHistoryStore.record(this, url, "", "", "canceled", "已取消下载");
            DownloadState.INSTANCE.canceled();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            DownloadHistoryStore.record(this, url, "", "", "canceled", "已取消下载");
            DownloadState.INSTANCE.canceled();
        } catch (Throwable failure) {
            String diagnostic;
            synchronized (processOutputTail) {
                diagnostic = processOutputTail.toString();
            }
            String message = formatDownloadError(failure, diagnostic);
            DownloadHistoryStore.record(this, url, "", "", "failed", message);
            DownloadState.INSTANCE.failed(message);
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
                        DownloadState.INSTANCE.started(remaining);
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

    private String extractSpeed(String outputLine) {
        if (outputLine == null || outputLine.isEmpty()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)\\bat\\s+([^\\s]+/s)")
                .matcher(outputLine);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String formatDownloadError(Throwable failure, String processOutputTail) {
        String detail = failure == null ? "" : failure.getMessage();
        if (detail == null || detail.trim().isEmpty() ||
                (failure != null && failure.getClass().getSimpleName().equals(detail.trim()))) {
            String output = processOutputTail == null ? "" : processOutputTail.trim();
            detail = output.isEmpty()
                    ? (failure == null ? "未知错误" : failure.getClass().getSimpleName())
                    : output;
        }
        if (detail.length() > 1800) detail = detail.substring(detail.length() - 1800);
        String normalized = detail.toLowerCase(java.util.Locale.ROOT);
        String friendly;
        if (normalized.contains("unsupported url") ||
                normalized.contains("no suitable extractor") ||
                normalized.contains("no extractor")) {
            friendly = "暂不支持这个链接，请复制具体的视频播放页地址";
        } else if (normalized.contains("403") || normalized.contains("forbidden")) {
            friendly = "网站拒绝访问（403），请尝试登录授权或稍后重试";
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

    private YoutubeDLRequest buildRequest(
            String url, File directory, String selectedFormat, boolean audioOnly) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("-o", new File(directory, "%(title).200B.%(ext)s").getAbsolutePath());
        request.addOption("--newline");
        request.addOption("--progress");
        // Emit a stable yt-dlp progress line. youtubedl-android parses
        // "[download] 12.3% ... ETA 00:12" from stdout for its callback.
        request.addOption("--progress-template",
                "download:[download] %(progress._percent_str)s of %(progress._total_bytes_str)s at %(progress._speed_str)s ETA 00:00");
        request.addOption("--progress-delta", "1");
        request.addOption("--no-colors");
        request.addOption("--no-mtime");
        request.addOption("--retries", "10");
        request.addOption("--fragment-retries", "10");
        request.addOption("--file-access-retries", "3");
        request.addOption("--embed-metadata");

        android.content.SharedPreferences preferences =
                getSharedPreferences("dog_settings", Context.MODE_PRIVATE);
        boolean downloadSubtitles = preferences.getBoolean("download_subtitles", false);
        boolean downloadPlaylist = preferences.getBoolean("download_playlist", false);
        int concurrentFragments = Math.max(1, Math.min(8, preferences.getInt("concurrent_fragments", 4)));
        request.addOption("--concurrent-fragments", Integer.toString(concurrentFragments));
        if (preferences.getBoolean("skip_existing", false)) {
            request.addOption("--download-archive", new File(getFilesDir(), "download-archive.txt").getAbsolutePath());
        }
        request.addOption(downloadPlaylist ? "--yes-playlist" : "--no-playlist");
        if (downloadPlaylist) request.addOption("--no-abort-on-error");
        if (audioOnly) {
            String audioSelector = selectedFormat == null ? "" : selectedFormat.trim();
            if (!audioSelector.isEmpty()) {
                if (!isSafeFormatSelector(audioSelector) || audioSelector.contains("+")) {
                    throw new IllegalArgumentException("无效的音频格式 ID");
                }
                request.addOption("-f", audioSelector);
            } else {
                request.addOption("-f", "ba/b");
            }
            request.addOption("-x");
            String audioFormat = preferences.getString("audio_format", "mp3");
            if (!"mp3".equals(audioFormat) && !"m4a".equals(audioFormat) &&
                    !"opus".equals(audioFormat) && !"flac".equals(audioFormat)) {
                audioFormat = "mp3";
            }
            request.addOption("--audio-format", audioFormat);
            request.addOption("--audio-quality", "0");
            if (preferences.getBoolean("embed_thumbnail", true)) {
                request.addOption("--embed-thumbnail");
            }
        } else {
            String format = selectedFormat == null ? "" : selectedFormat.trim();
            if (!format.isEmpty()) {
                if (!isSafeFormatSelector(format)) {
                    throw new IllegalArgumentException("无效的视频/音频格式 ID");
                }
            } else {
                int maxHeight = preferences.getInt("max_height", 0);
                format = maxHeight > 0
                        ? "bv*[height=" + maxHeight + "]+ba/b[height=" + maxHeight + "]"
                        : "bv*+ba/b";
            }
            request.addOption("-f", format);
            String videoContainer = preferences.getString("video_container", "mp4");
            if (!"mp4".equals(videoContainer) && !"mkv".equals(videoContainer) &&
                    !"webm".equals(videoContainer)) {
                videoContainer = "mp4";
            }
            request.addOption("--merge-output-format", videoContainer);
        }

        if (downloadSubtitles) {
            request.addOption("--write-subs");
            request.addOption("--write-auto-subs");
            String subtitleLanguages = preferences.getString("subtitle_languages", "all");
            if (subtitleLanguages == null || subtitleLanguages.trim().isEmpty()) {
                subtitleLanguages = "all";
            }
            request.addOption("--sub-langs", subtitleLanguages.trim());
            if (!audioOnly) request.addOption("--embed-subs");
        }

        // aria2c is the upstream native Android downloader, initialized by YtDlpEngine.
        request.addOption("--downloader", "libaria2c.so");
        return request;
    }

    private boolean isSafeFormatSelector(String selector) {
        return selector != null && selector.matches(
                "[A-Za-z0-9._-]{1,80}(?:\\+[A-Za-z0-9._-]{1,80})?"
        );
    }

    private File getDownloadDirectory() throws IOException {
        File base;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("需要存储权限才能写入系统下载目录");
            }
            base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        } else {
            base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (base == null) base = getFilesDir();
        }

        File directory = new File(base, "Dog");
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
            pendingUrls.clear();
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
                .setContentTitle("Dog 下载器")
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
                "dog_settings", Context.MODE_PRIVATE).getBoolean("audio_only", false);
        start(context, url, "", audioOnly);
    }

    public static void start(Context context, String url, String formatSelector, boolean audioOnly) {
        Intent intent = new Intent(context, DownloadService.class)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FORMAT_SELECTOR, formatSelector == null ? "" : formatSelector)
                .putExtra(EXTRA_AUDIO_ONLY, audioOnly);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_CANCEL);
        context.startService(intent);
    }

    /** Clears only pending URLs. The currently running download is left untouched. */
    public static void clearQueue(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_CLEAR_QUEUE);
        context.startService(intent);
    }
}
