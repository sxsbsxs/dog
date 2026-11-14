package com.sxsbsxs.dog;

import android.content.Context;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches source metadata and the exact formats exposed by yt-dlp. */
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
        public final String thumbnail;
        public final List<QualityOption> qualities;
        public final String qualityNotice;
        public final int videoFormatCount;

        Preview(String title, String uploader, String duration, String resolution,
                String fileSize, String extractor, String thumbnail,
                List<QualityOption> qualities, String qualityNotice, int videoFormatCount) {
            this.title = title;
            this.uploader = uploader;
            this.duration = duration;
            this.resolution = resolution;
            this.fileSize = fileSize;
            this.extractor = extractor;
            this.thumbnail = thumbnail;
            this.qualities = Collections.unmodifiableList(new ArrayList<>(qualities));
            this.qualityNotice = qualityNotice;
            this.videoFormatCount = videoFormatCount;
        }
    }

    public static final class QualityOption {
        public final int height;
        public final int fps;
        public final String label;

        QualityOption(int height, int fps, String label) {
            this.height = height;
            this.fps = fps;
            this.label = label;
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
                request.addOption("--dump-json");
                cookieFile = SecureCookieStore.createTemporaryCookieFile(app);
                if (cookieFile != null) {
                    request.addOption("--cookies", cookieFile.getAbsolutePath());
                }

                // Use the raw JSON output so every entry in yt-dlp's formats[]
                // is inspected, rather than trusting a partially mapped model.
                YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, null, false, null);
                String rawOutput = response.getOut();
                int jsonStart = rawOutput == null ? -1 : rawOutput.indexOf('{');
                String screenMessages = jsonStart > 0 ? rawOutput.substring(0, jsonStart) : "";
                String extractionDiagnostics = response.getErr() + "\\n" + screenMessages;
                JSONObject root = parseDumpJson(rawOutput);
                String title = firstNonEmpty(root.optString("title", ""), root.optString("fulltitle", ""), "未知标题");
                String uploader = firstNonEmpty(root.optString("uploader", ""), root.optString("uploader_id", ""));
                String duration = formatDuration(root.optLong("duration", 0L));
                String resolution = firstNonEmpty(root.optString("resolution", ""));
                int videoWidth = root.optInt("width", 0);
                int videoHeight = root.optInt("height", 0);
                if (resolution.isEmpty() && videoHeight > 0) {
                    resolution = videoWidth > 0 ? videoWidth + "×" + videoHeight : videoHeight + "p";
                }
                long bytes = root.optLong("filesize_approx", 0L);
                if (bytes <= 0L) bytes = root.optLong("filesize", 0L);
                String size = bytes > 0L ? formatSize(bytes) : "未知";
                String extractor = firstNonEmpty(root.optString("extractor", ""),
                        root.optString("extractor_key", ""), "yt-dlp");
                String thumbnail = firstNonEmpty(root.optString("thumbnail", ""));

                JSONArray rawFormats = root.optJSONArray("formats");
                List<QualityOption> qualities = buildQualityOptions(rawFormats);
                int videoFormatCount = countVideoFormats(rawFormats);
                boolean bilibili = isBilibiliUrl(url);
                boolean hasSessData = hasCookie(cookieFile, "SESSDATA");
                String diagnostic = buildQualityNotice(
                        bilibili, hasSessData, qualities, videoFormatCount, extractionDiagnostics);

                Preview preview = new Preview(title, uploader, duration, resolution, size,
                        extractor, thumbnail, qualities, diagnostic, videoFormatCount);
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

    private static JSONObject parseDumpJson(String output) throws JSONException {
        if (output == null) throw new JSONException("yt-dlp 没有返回视频信息");
        int jsonStart = output.indexOf('{');
        if (jsonStart < 0) throw new JSONException("yt-dlp 返回内容中没有格式信息");
        return new JSONObject(output.substring(jsonStart).trim());
    }

    private static List<QualityOption> buildQualityOptions(JSONArray formats) {
        // Aggregate the exact heights exposed by yt-dlp. Keep the best FPS per height.
        Map<Integer, Integer> fpsByHeight = new TreeMap<>(Collections.reverseOrder());
        if (formats != null) {
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.optJSONObject(i);
                if (format == null) continue;
                int height = format.optInt("height", 0);
                if (height <= 0) height = parseHeight(format.optString("resolution", ""));
                if (height <= 0) continue;
                String videoCodec = format.optString("vcodec", "");
                if ("none".equalsIgnoreCase(videoCodec)) continue;
                int fps = (int) Math.round(format.optDouble("fps", 0.0));
                fpsByHeight.merge(height, Math.max(0, fps), Math::max);
            }
        }

        List<QualityOption> qualities = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : fpsByHeight.entrySet()) {
            int height = entry.getKey();
            int fps = entry.getValue();
            String label;
            if (height == 4320) label = "8K · " + height + "p";
            else if (height == 2160) label = "4K · " + height + "p";
            else label = height + "p";
            if (fps >= 48) label += " · " + fps + "fps";
            qualities.add(new QualityOption(height, fps, label));
        }
        return qualities;
    }

    private static int countVideoFormats(JSONArray formats) {
        int count = 0;
        if (formats == null) return count;
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.optJSONObject(i);
            if (format == null) continue;
            int height = format.optInt("height", 0);
            if (height <= 0) height = parseHeight(format.optString("resolution", ""));
            if (height <= 0) continue;
            if ("none".equalsIgnoreCase(format.optString("vcodec", ""))) continue;
            count++;
        }
        return count;
    }

    private static int parseHeight(String resolution) {
        if (resolution == null || resolution.isEmpty()) return 0;
        Matcher matcher = Pattern.compile("(?:x|^)(\\d{3,4})(?:p|$)", Pattern.CASE_INSENSITIVE)
                .matcher(resolution);
        return matcher.find() ? safeParseInt(matcher.group(1)) : 0;
    }

    private static int safeParseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private static String buildQualityNotice(
            boolean bilibili, boolean hasSessData, List<QualityOption> qualities,
            int videoFormatCount, String stderr
    ) {
        if (bilibili && !hasSessData) {
            return "未检测到 B 站 SESSDATA 登录凭据，当前可能只能获取到低清画质。请重新完成 B 站授权后再解析。";
        }

        if (bilibili && stderr != null) {
            Matcher missing = Pattern.compile("Format\\(s\\) (.+?) are missing", Pattern.CASE_INSENSITIVE)
                    .matcher(stderr);
            if (missing.find()) {
                return "B 站本次没有向解析器提供这些画质：" + missing.group(1) +
                        "。请确认账号/大会员权限，并在“我的 → 解析引擎”更新 yt-dlp。";
            }
        }

        if (qualities.isEmpty()) {
            return "解析器没有收到可用的视频画质列表；下载时会由源站选择默认格式。";
        }

        if (bilibili && qualities.size() == 1 && qualities.get(0).height <= 480) {
            return "B 站本次只返回了 " + qualities.get(0).height +
                    "p（解析到 " + videoFormatCount + " 个视频格式）。网页能播放更高清晰度但这里没有返回时，通常是授权状态或 B 站接口/解析器限制。";
        }
        return "";
    }

    private static boolean hasCookie(File cookieFile, String cookieName) {
        if (cookieFile == null || !cookieFile.isFile()) return false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(cookieFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Netscape cookie jars prefix HttpOnly rows with #HttpOnly_.
                // These are real cookies (including SESSDATA), not comments.
                if (line.isEmpty() || (line.startsWith("#") && !line.startsWith("#HttpOnly_"))) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length >= 7 && cookieName.equals(fields[5]) && !fields[6].isEmpty()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Do not expose or log cookie contents.
        }
        return false;
    }

    private static boolean isBilibiliUrl(String url) {
        if (url == null) return false;
        try {
            String host = java.net.URI.create(url).getHost();
            return host != null && (host.equalsIgnoreCase("bilibili.com") ||
                    host.toLowerCase(Locale.ROOT).endsWith(".bilibili.com"));
        } catch (Exception ignored) {
            return url.toLowerCase(Locale.ROOT).contains("bilibili.com");
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
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
