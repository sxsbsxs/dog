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
import java.util.concurrent.atomic.AtomicInteger;
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
        public final List<FormatOption> formats;
        public final String qualityNotice;
        public final int videoFormatCount;

        Preview(String title, String uploader, String duration, String resolution,
                String fileSize, String extractor, String thumbnail,
                List<QualityOption> qualities, List<FormatOption> formats,
                String qualityNotice, int videoFormatCount) {
            this.title = title;
            this.uploader = uploader;
            this.duration = duration;
            this.resolution = resolution;
            this.fileSize = fileSize;
            this.extractor = extractor;
            this.thumbnail = thumbnail;
            this.qualities = Collections.unmodifiableList(new ArrayList<>(qualities));
            this.formats = Collections.unmodifiableList(new ArrayList<>(formats));
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

    public static final class FormatOption {
        public final String formatId;
        public final String ext;
        public final String vcodec;
        public final String acodec;
        public final int width;
        public final int height;
        public final int fps;
        public final long filesize;
        public final double bitrate;
        public final boolean hasVideo;
        public final boolean hasAudio;
        public final String label;
        public final String details;

        FormatOption(String formatId, String ext, String vcodec, String acodec,
                     int width, int height, int fps, long filesize, double bitrate) {
            this.formatId = formatId;
            this.ext = ext;
            this.vcodec = vcodec;
            this.acodec = acodec;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.filesize = filesize;
            this.bitrate = bitrate;
            this.hasVideo = !isMissingCodec(vcodec);
            this.hasAudio = !isMissingCodec(acodec);
            this.label = buildFormatLabel(this);
            this.details = buildFormatDetails(this);
        }
    }

    private static final AtomicInteger ACTIVE_INSPECTIONS = new AtomicInteger(0);

    private VideoInfoService() {}

    public static boolean isInspecting() {
        return ACTIVE_INSPECTIONS.get() > 0;
    }

    public static void inspect(Context context, String url, Callback callback) {
        Context app = context.getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ACTIVE_INSPECTIONS.incrementAndGet();
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
                String extractionDiagnostics = response.getErr() + "\n" + screenMessages;
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
                List<FormatOption> formats = buildFormatOptions(rawFormats);
                int videoFormatCount = countVideoFormats(rawFormats);
                boolean bilibili = isBilibiliUrl(url);
                boolean hasSessData = hasCookie(cookieFile, "SESSDATA");
                boolean niconico = isNiconicoUrl(url);
                boolean hasNiconicoSession = hasCookie(cookieFile, "user_session") ||
                        hasCookie(cookieFile, "user_session_secure");
                String diagnostic = buildQualityNotice(
                        bilibili, hasSessData, niconico, hasNiconicoSession,
                        qualities, videoFormatCount, extractionDiagnostics);

                Preview preview = new Preview(title, uploader, duration, resolution, size,
                        extractor, thumbnail, qualities, formats, diagnostic, videoFormatCount);
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
                ACTIVE_INSPECTIONS.decrementAndGet();
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

    private static List<FormatOption> buildFormatOptions(JSONArray rawFormats) {
        List<FormatOption> result = new ArrayList<>();
        if (rawFormats == null) return result;
        for (int i = 0; i < rawFormats.length(); i++) {
            JSONObject item = rawFormats.optJSONObject(i);
            if (item == null) continue;
            String id = firstNonEmpty(item.optString("format_id", ""));
            // Preserve literal IDs only; selector expressions are assembled by Dog.
            if (!id.matches("[A-Za-z0-9._-]{1,80}")) continue;
            String vcodec = firstNonEmpty(item.optString("vcodec", ""), "none");
            String acodec = firstNonEmpty(item.optString("acodec", ""), "none");
            if (isMissingCodec(vcodec) && isMissingCodec(acodec)) continue;
            int height = item.optInt("height", 0);
            if (height <= 0) height = parseHeight(item.optString("resolution", ""));
            int width = item.optInt("width", 0);
            int fps = (int) Math.round(item.optDouble("fps", 0.0));
            long bytes = item.optLong("filesize", 0L);
            if (bytes <= 0L) bytes = item.optLong("filesize_approx", 0L);
            double bitrate = item.optDouble("tbr", 0.0);
            result.add(new FormatOption(
                    id, firstNonEmpty(item.optString("ext", "")), vcodec, acodec,
                    width, Math.max(0, height), Math.max(0, fps),
                    Math.max(0L, bytes), Math.max(0.0, bitrate)
            ));
        }
        Collections.sort(result, (left, right) -> {
            if (left.hasVideo != right.hasVideo) return left.hasVideo ? -1 : 1;
            if (left.hasAudio != right.hasAudio) return left.hasAudio ? 1 : -1;
            int height = Integer.compare(right.height, left.height);
            if (height != 0) return height;
            int fps = Integer.compare(right.fps, left.fps);
            if (fps != 0) return fps;
            return Double.compare(right.bitrate, left.bitrate);
        });
        return result;
    }

    private static boolean isMissingCodec(String codec) {
        return codec == null || codec.trim().isEmpty() || "none".equalsIgnoreCase(codec.trim());
    }

    private static String buildFormatLabel(FormatOption format) {
        String label = format.hasVideo
                ? (format.height > 0 ? format.height + "p" : "视频")
                : "音频";
        if (format.hasVideo && format.fps >= 48) label += " · " + format.fps + "fps";
        String codec;
        if (format.hasVideo && format.hasAudio) {
            codec = codecLabel(format.vcodec) + " + " + codecLabel(format.acodec);
        } else {
            codec = codecLabel(format.hasVideo ? format.vcodec : format.acodec);
        }
        if (!codec.isEmpty()) label += " · " + codec;
        return label;
    }

    private static String buildFormatDetails(FormatOption format) {
        List<String> parts = new ArrayList<>();
        parts.add("ID " + format.formatId);
        if (!format.ext.isEmpty()) parts.add(format.ext.toUpperCase(Locale.ROOT));
        if (format.width > 0 && format.height > 0) parts.add(format.width + "×" + format.height);
        if (format.filesize > 0L) parts.add(formatSize(format.filesize));
        else if (format.bitrate > 0.0) parts.add(String.format(Locale.ROOT, "%.0f kb/s", format.bitrate));
        return join(parts, " · ");
    }

    private static String codecLabel(String codec) {
        if (codec == null || codec.isEmpty()) return "";
        String value = codec.toLowerCase(Locale.ROOT);
        if (value.startsWith("avc") || value.contains("h264")) return "H.264";
        if (value.startsWith("hev") || value.startsWith("hvc") || value.contains("h265")) return "H.265";
        if (value.startsWith("vp09") || value.startsWith("vp9")) return "VP9";
        if (value.startsWith("av01")) return "AV1";
        if (value.startsWith("opus")) return "Opus";
        if (value.startsWith("mp4a") || value.startsWith("aac")) return "AAC";
        if (value.startsWith("flac")) return "FLAC";
        return codec.length() > 18 ? codec.substring(0, 18) : codec;
    }

    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
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
            boolean bilibili, boolean hasSessData,
            boolean niconico, boolean hasNiconicoSession,
            List<QualityOption> qualities, int videoFormatCount, String stderr
    ) {
        // Anonymous Bilibili playback can expose HD formats too.
        if (bilibili && stderr != null) {
            Matcher missing = Pattern.compile("Format\\(s\\) (.+?) are missing", Pattern.CASE_INSENSITIVE)
                    .matcher(stderr);
            if (missing.find()) {
                return "B 站本次没有向解析器提供这些画质：" + missing.group(1) +
                        "。请确认账号/大会员权限，并在“我的 → 解析引擎”更新 yt-dlp。";
            }
        }

        if (qualities.isEmpty()) {
            if (niconico && !hasNiconicoSession) {
                return "当前以 Niconico 游客身份解析，但没有收到可用的视频流。请登录 Niconico 后重新解析；能否获得更高清晰度仍取决于视频本身和账号权限。";
            }
            return "解析器没有收到可用的视频画质列表；下载时会由源站选择默认格式。";
        }

        int highestHeight = qualities.get(0).height;
        if (niconico && !hasNiconicoSession && highestHeight <= 720) {
            return "当前以 Niconico 游客身份解析，接口本次最高只返回 " + highestHeight +
                    "p。Niconico 的访客接口可能不提供 Premium 高画质流；登录账号后重新解析，才有机会看到 1080p。是否可用仍取决于该视频及账号权限。";
        }
        if (niconico && hasNiconicoSession && highestHeight <= 720) {
            return "已检测到 Niconico 登录凭据，但该视频本次仍只返回到 " + highestHeight +
                    "p。Dog 没有把 1080p 限制成 720p；更高规格可能未对当前账号开放，或该视频本身没有更高流。";
        }

        if (bilibili && qualities.size() == 1 && qualities.get(0).height <= 480) {
            String accessState = hasSessData ? "已检测到登录凭据" : "当前为匿名访问";
            return "B 站本次只返回了 " + qualities.get(0).height +
                    "p（解析到 " + videoFormatCount + " 个视频格式，" + accessState +
                    "）。匿名请求也可能获取高清画质；实际结果取决于该视频、权限和站点接口，登录不保证解锁更高画质。";
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

    private static boolean isNiconicoUrl(String url) {
        if (url == null) return false;
        try {
            String host = java.net.URI.create(url).getHost();
            return host != null && (host.equalsIgnoreCase("nicovideo.jp") ||
                    host.toLowerCase(Locale.ROOT).endsWith(".nicovideo.jp"));
        } catch (Exception ignored) {
            return url.toLowerCase(Locale.ROOT).contains("nicovideo.jp");
        }
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
