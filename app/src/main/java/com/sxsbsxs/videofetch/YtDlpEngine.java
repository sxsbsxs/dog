package com.sxsbsxs.videofetch;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.yausername.aria2c.Aria2c;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

/**
 * Initializes the device-local media runtime.
 *
 * CPython hosts yt-dlp. FFmpeg and aria2c are native Android packages.
 */
public final class YtDlpEngine {
    private static volatile boolean initialized;
    private static final String ASSET_PREFIX = "yt-dlp-plugins/";
    private static final String[] PLUGIN_ASSETS = {
            ASSET_PREFIX + "videofetch/yt_dlp_plugins/extractor/niconico.py",
            ASSET_PREFIX + "videofetch/yt_dlp_plugins/extractor/kuaishou.py",
            ASSET_PREFIX + "videofetch/yt_dlp_plugins/extractor/douyin.py"
    };
    private static volatile File pluginDirectory;

    private YtDlpEngine() {}

    public static synchronized void initialize(Context context) throws Exception {
        if (initialized) return;

        Context appContext = context.getApplicationContext();
        getPluginDirectory(appContext);
        YoutubeDL.getInstance().init(appContext);
        FFmpeg.getInstance().init(appContext);
        Aria2c.getInstance().init(appContext);
        initialized = true;
    }

    /**
     * Installs the app-owned yt-dlp plugins outside the updater-managed
     * yt-dlp binary directory, so engine updates cannot delete them.
     */
    public static synchronized File getPluginDirectory(Context context) throws IOException {
        if (pluginDirectory != null && pluginDirectory.isDirectory()) return pluginDirectory;

        Context appContext = context.getApplicationContext();
        File root = new File(appContext.getNoBackupFilesDir(), "videofetch-yt-dlp-plugins");

        // Copy the complete package on first access after app start. This also
        // upgrades files left behind by an earlier app version in noBackupFilesDir.
        for (String assetPath : PLUGIN_ASSETS) {
            String relativePath = assetPath.substring(ASSET_PREFIX.length());
            File destination = new File(root, relativePath);
            File parent = destination.getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                throw new IOException("无法创建 yt-dlp 解析插件目录");
            }

            try (InputStream input = appContext.getAssets().open(assetPath);
                 FileOutputStream output = new FileOutputStream(destination, false)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
        }

        pluginDirectory = root;
        return root;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
