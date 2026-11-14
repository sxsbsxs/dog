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
 * and publication into Android's public Downloads collection.
 */
public class DownloadService extends Service {
    public static final String EXTRA_URL = "extra_url";
    public static final String ACTION_CANCEL = "com.sxsbsxs.dog.CANCEL_DOWNLOAD";

    private static final String TAG = "DogDownload";
    private static final String CHANNEL_ID = "dog_downloads";
    private static final int NOTIFICATION_ID = 7421;
    private static final int CANCEL_REQUEST_ID = 7422;
    private static final String PROCESS_ID = "dog-download-process";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Future<?> downloadFuture;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelDownload();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        String url = intent == null ? null : intent.getStringExtra(EXTRA_URL);
        if (!isHttpUrl(url)) {
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

    private boolean isHttpUrl(String value) {
        return value != null &&
                (value.regionMatches(true, 0, "https://", 0, 8) ||
                 value.regionMatches(true, 0, "http://", 0, 7));
    }

    private void runDownload(String url, int startId) {
        try {
            File outputDirectory = getDownloadDirectory();
            Map<String, String> before = snapshotFiles(outputDirectory);
            YtDlpEngine.initialize(getApplicationContext());

            YoutubeDLRequest request = buildRequest(url, outputDirectory);
            String cookie = SecureCookieStore.load(getApplicationContext());
            if (cookie != null) {
                cookie = cookie.trim();
                if (cookie.regionMatches(true, 0, "Cookie:", 0, 7)) {
                    cookie = cookie.substring(7).trim();
                }
            }
            if (cookie != null && !cookie.isEmpty()) {
                request.addOption("--add-header", "Cookie: " + cookie);
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
            if (completedFiles.isEmpty()) {
                throw new IOException("下载任务已结束，但没有找到新的视频或音频文件");
            }

            int publishedCount = 0;
            int publishFailures = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (File file : completedFiles) {
                    try {
                        MediaStorePublisher.publish(this, file);
                        publishedCount++;
                        // Avoid retaining a second full-size copy after public publishing succeeds.
                        if (!file.delete()) {
                            Log.w(TAG, "Published file but could not remove staging copy: " + file);
                        }
                    } catch (Exception publishFailure) {
                        publishFailures++;
                        Log.e(TAG, "Could not publish " + file.getName(), publishFailure);
                    }
                }
            } else {
                publishedCount = completedFiles.size();
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
            DownloadState.INSTANCE.canceled();
            removeNotification();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            DownloadState.INSTANCE.canceled();
            removeNotification();
        } catch (Throwable failure) {
            String detail = failure.getMessage() == null ? "未知错误" : failure.getMessage();
            if (detail.length() > 180) detail = detail.substring(0, 180) + "…";
            String message = "下载失败：" + detail;
            DownloadState.INSTANCE.failed(message);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(message, 0, false));
            }
            Log.e(TAG, message, failure);
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
        request.addOption("--retries", "10");
        request.addOption("--fragment-retries", "10");
        request.addOption("--file-access-retries", "3");
        request.addOption("--concurrent-fragments", "4");

        // aria2c is the upstream native Android downloader, initialized by YtDlpEngine.
        request.addOption("--downloader", "libaria2c.so");
        return request;
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
        try {
            YoutubeDL.destroyProcessById(PROCESS_ID);
        } catch (Throwable ignored) {
            // The process may have exited before the cancel action arrived.
        }

        Future<?> future = downloadFuture;
        if (future != null) future.cancel(true);
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
        Intent intent = new Intent(context, DownloadService.class).putExtra(EXTRA_URL, url);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_CANCEL);
        context.startService(intent);
    }
}
