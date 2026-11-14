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

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * Java owns the complete download lifecycle: request construction, Cookie
 * injection, native downloader selection, progress notifications, cancellation
 * and publication into the Android Downloads collection.
 */
public class DownloadService extends Service {
    public static final String EXTRA_URL = "extra_url";
    public static final String ACTION_CANCEL = "com.sxsbsxs.dog.CANCEL_DOWNLOAD";

    private static final String CHANNEL_ID = "dog_downloads";
    private static final int NOTIFICATION_ID = 7421;
    private static final String PROCESS_ID = "dog-download-process";
    private static final String PREFERENCES = "dog_settings";
    private static final String COOKIE_KEY = "cookie";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Future<?> downloadFuture;

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

        String url = intent == null ? null : intent.getStringExtra(EXTRA_URL);
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        if (!running.compareAndSet(false, true)) {
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("正在准备下载", 0, true));
        DownloadState.INSTANCE.started();

        downloadFuture = executor.submit(() -> runDownload(url, startId));
        return START_NOT_STICKY;
    }

    private void runDownload(String url, int startId) {
        try {
            File outputDirectory = getDownloadDirectory();
            Map<String, String> before = snapshotFiles(outputDirectory);
            YtDlpEngine.initialize(getApplicationContext());

            YoutubeDLRequest request = buildRequest(url, outputDirectory);
            String cookie = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(COOKIE_KEY, "");
            if (cookie != null) {
                cookie = cookie.trim();
                if (cookie.regionMatches(true, 0, "Cookie:", 0, 7)) {
                    cookie = cookie.substring(7).trim();
                }
            }
            if (cookie != null && !cookie.isEmpty()) {
                request.addOption("--add-header", "Cookie:" + cookie);
            }

            final int[] lastProgress = {-1};
            Function3<Float, Long, String, Unit> progressCallback = (progress, eta, outputLine) -> {
                int percent = progress == null ? 0 : Math.max(0, Math.min(100, progress.intValue()));
                String message = eta != null && eta > 0
                        ? "正在下载 · " + percent + "% · 剩余约 " + eta + " 秒"
                        : "正在下载 · " + percent + "%";

                DownloadState.INSTANCE.updateProgress(percent, message);
                if (percent != lastProgress[0]) {
                    lastProgress[0] = percent;
                    NotificationManager manager = getSystemService(NotificationManager.class);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, buildNotification(message, percent, true));
                    }
                }
                return Unit.INSTANCE;
            };

            YoutubeDL.getInstance().execute(request, PROCESS_ID, progressCallback);

            List<File> completedFiles = findCompletedFiles(outputDirectory, before);
            int publishedCount = 0;
            for (File file : completedFiles) {
                try {
                    MediaStorePublisher.publish(this, file);
                    publishedCount++;
                } catch (Exception ignored) {
                    // Keep the original private download available if public publishing fails.
                }
            }

            String location;
            String finishedMessage;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && publishedCount > 0) {
                location = "公共下载目录：Download/Dog（" + publishedCount + " 个文件）";
                finishedMessage = "下载完成，已保存到 Download/Dog";
            } else {
                location = outputDirectory.getAbsolutePath();
                finishedMessage = "下载完成";
            }

            DownloadState.INSTANCE.completed(location);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID,
                        buildNotification(finishedMessage, 100, false));
            }
        } catch (YoutubeDL.CanceledException canceled) {
            DownloadState.INSTANCE.canceled();
            removeNotification();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            DownloadState.INSTANCE.canceled();
            removeNotification();
        } catch (Throwable failure) {
            String detail = failure.getMessage() == null ? "未知错误" : failure.getMessage();
            DownloadState.INSTANCE.failed("下载失败：" + detail);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID,
                        buildNotification("下载失败：" + detail, 0, false));
            }
        } finally {
            running.set(false);
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelfResult(startId);
        }
    }

    private YoutubeDLRequest buildRequest(String url, File directory) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("-o", new File(directory, "%(title).200B.%(ext)s").getAbsolutePath());
        request.addOption("-f", "bv*+ba/b");
        request.addOption("--merge-output-format", "mp4");
        request.addOption("--newline");
        request.addOption("--progress");
        request.addOption("--no-mtime");
        request.addOption("--concurrent-fragments", "4");
        request.addOption("--no-warnings");

        // aria2c is the upstream native Android downloader, initialized by YtDlpEngine.
        request.addOption("--downloader", "libaria2c.so");
        return request;
    }

    private File getDownloadDirectory() {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) base = getFilesDir();
        File directory = new File(base, "Dog");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create download directory: " + directory);
        }
        return directory;
    }

    private Map<String, String> snapshotFiles(File directory) {
        Map<String, String> result = new HashMap<>();
        File[] files = directory.listFiles();
        if (files == null) return result;

        for (File file : files) {
            if (file.isFile() && file.length() > 0L) {
                result.put(file.getAbsolutePath(),
                        file.length() + ":" + file.lastModified());
            }
        }
        return result;
    }

    private List<File> findCompletedFiles(File directory, Map<String, String> before) {
        List<File> result = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files == null) return result;

        for (File file : files) {
            if (!file.isFile() || file.length() <= 0L) continue;
            String lowerName = file.getName().toLowerCase(java.util.Locale.ROOT);
            if (lowerName.endsWith(".part") || lowerName.endsWith(".ytdl") ||
                    lowerName.endsWith(".temp") || lowerName.endsWith(".tmp")) {
                continue;
            }

            String current = file.length() + ":" + file.lastModified();
            if (!current.equals(before.get(file.getAbsolutePath()))) {
                result.add(file);
            }
        }
        return result;
    }

    private void cancelDownload() {
        try {
            YoutubeDL.destroyProcessById(PROCESS_ID);
        } catch (Throwable ignored) {
            // The process may have exited before the cancel action arrived.
        }

        if (downloadFuture != null) downloadFuture.cancel(true);
        running.set(false);
        DownloadState.INSTANCE.canceled();
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
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Dog 下载器")
                .setContentText(message)
                .setContentIntent(openPendingIntent)
                .setProgress(100, Math.max(0, Math.min(100, progress)), ongoing && progress == 0)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .build();
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
        Intent intent = new Intent(context, DownloadService.class)
                .putExtra(EXTRA_URL, url);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, DownloadService.class)
                .setAction(ACTION_CANCEL);
        context.startService(intent);
    }
}
