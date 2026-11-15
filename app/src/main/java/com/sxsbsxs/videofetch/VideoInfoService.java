package com.sxsbsxs.videofetch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

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
        public final String niconicoApiMode;

        Preview(String title, String uploader, String duration, String resolution,
                String fileSize, String extractor, String thumbnail,
                List<QualityOption> qualities, List<FormatOption> formats,
                String qualityNotice, int videoFormatCount, String niconicoApiMode) {
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
            this.niconicoApiMode = niconicoApiMode == null ? "" : niconicoApiMode;
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
                cookieFile = SecureCookieStore.createTemporaryCookieFile(app);
                YoutubeDLRequest request = createInfoRequest(app, url, cookieFile, null);

                // Use the raw JSON output so every entry in yt-dlp's formats[]
                // is inspected, rather than trusting a partially mapped model.
                YoutubeDLResponse response = executeWithYouTubeFallback(
                        app, url, cookieFile, request);
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
                // Match yt-dlp's NiconicoBaseIE.is_logged_in exactly:
                // the extractor checks user_session on www.nicovideo.jp.
                boolean hasNiconicoSession = hasCookieForHost(
                        cookieFile, "user_session", "www.nicovideo.jp");
                String niconicoApiMode = niconico
                        ? root.optString("videofetch_niconico_api_mode", "")
                        : "";
                String diagnostic = buildQualityNotice(
                        bilibili, hasSessData, niconico, hasNiconicoSession, niconicoApiMode,
                        qualities, videoFormatCount, extractionDiagnostics);

                Preview preview = new Preview(title, uploader, duration, resolution, size,
                        extractor, thumbnail, qualities, formats, diagnostic, videoFormatCount,
                        niconicoApiMode);
                // Every consumer updates Compose state and may show Android UI.
                // Always cross the main-thread boundary from this worker executor.
                MAIN_HANDLER.post(() -> callback.onSuccess(preview));
            } catch (Throwable failure) {
                String failureText = failure.toString();
                String message = YouTubeRequestFallback.isJavaScriptRuntimeFailure(failureText) ||
                        YouTubeRequestFallback.shouldRetry(url, failure, failureText)
                        ? YouTubeRequestFallback.userMessage(failureText)
                        : failure.getMessage();
                if (message == null || message.trim().isEmpty()) message = failure.getClass().getSimpleName();
                if (message.length() > 360) message = message.substring(0, 360) + "…";
                final String callbackMessage = message;
                MAIN_HANDLER.post(() -> callback.onError(callbackMessage));
            } finally {
                if (cookieFile != null && cookieFile.exists() && !cookieFile.delete()) {
                    cookieFile.deleteOnExit();
                }
                ACTIVE_INSPECTIONS.decrementAndGet();
                executor.shutdown();
            }
        });
    }

    private static YoutubeDLRequest createInfoRequest(
            Context app, String url, File cookieFile, String playerClient
    ) throws java.io.IOException {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--plugin-dirs", YtDlpEngine.getPluginDirectory(app).getAbsolutePath());
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        request.addOption("--dump-json");
        if (YouTubeRequestFallback.isYoutubeUrl(url)) {
            YouTubeRequestFallback.applyCommonOptions(request);
        }
        if (cookieFile != null) {
            request.addOption("--cookies", cookieFile.getAbsolutePath());
        }
        if ("tv".equals(playerClient)) {
            YouTubeRequestFallback.applyFallback(request);
        } else if ("web_safari".equals(playerClient)) {
            YouTubeRequestFallback.applyHlsFallback(request);
        }
        return request;
    }

    private static YoutubeDLResponse executeWithYouTubeFallback(
            Context app, String url, File cookieFile, YoutubeDLRequest primaryRequest
    ) throws Exception {
        try {
            return YoutubeDL.getInstance().execute(primaryRequest, null, false, null);
        } catch (Exception firstFailure) {
            if (!YouTubeRequestFallback.shouldRetry(url, firstFailure, firstFailure.getMessage())) {
                throw firstFailure;
            }

            YoutubeDLRequest tvRequest = createInfoRequest(app, url, cookieFile, "tv");
            try {
                return YoutubeDL.getInstance().execute(tvRequest, null, false, null);
            } catch (Exception tvFailure) {
                YoutubeDLRequest safariRequest = createInfoRequest(app, url, cookieFile, "web_safari");
                try {
                    return YoutubeDL.getInstance().execute(safariRequest, null, false, null);
                } catch (Exception safariFailure) {
                    safariFailure.addSuppressed(tvFailure);
                    safariFailure.addSuppressed(firstFailure);
                    throw safariFailure;
                }
            }
        }
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
            // Preserve literal IDs only; selector expressions are assembled by VideoFetch.
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
            boolean niconico, boolean hasNiconicoSession, String niconicoApiMode,
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
            if (niconico) {
                return "Niconico " + niconicoRouteLabel(niconicoApiMode) +
                        "没有返回可用视频流。VideoFetch 会尝试使用上游 v3/v3_guest 提取器；请检查视频可用性和账号权限。";
            }
            return "解析器没有收到可用的视频画质列表；下载时会由源站选择默认格式。";
        }

        int highestHeight = qualities.get(0).height;
        if (niconico && highestHeight <= 720) {
            if ("v3-dms".equals(niconicoApiMode) || "v3".equals(niconicoApiMode)) {
                return "本次通过 Niconico 标准 v3 登录接口获取到最高 " + highestHeight +
                        "p。标准 v3 需要 user_session，说明本次解析走的是登录接口，不是游客接口。" +
                        "Niconico 官方规则：普通会员最高 720p，Premium 最高 1080p。" +
                        "如果你确认账号是 Premium，而且同一账号在 Niconico 官网能播放该视频的 1080p，" +
                        "那么当前 DMS 返回的数据与官网不一致，问题在接口/会话取流，不是画质选择界面把 1080p 隐藏了。";
            }
            if ("v3_guest-dms".equals(niconicoApiMode) || "v3_guest".equals(niconicoApiMode)) {
                return "本次最终使用了 Niconico 游客接口，实际只返回最高 " + highestHeight +
                        "p。虽然应用可能保存了 Cookie，但标准 v3 登录接口没有成功提供可用流，" +
                        "所以这次结果不能算已使用登录画质权限。请重新授权并确认账号已登录。" +
                        "普通会员官方最高 720p，Premium 最高 1080p。";
            }
            String account = hasNiconicoSession
                    ? "本机 Cookie 文件中检测到了 user_session"
                    : "本机 Cookie 文件中没有检测到 user_session";
            return "本次通过 " + niconicoRouteLabel(niconicoApiMode) +
                    " 获取到最高 " + highestHeight + "p（" + account +
                    "）。Niconico 官方规则：普通会员最高 720p，Premium 最高 1080p。" +
                    "如果你是 Premium 且官网同一视频可播放 1080p，说明当前解析路径没有拿到官网可用的完整流。";
        }
        if (niconico && highestHeight > 720 && !hasNiconicoSession) {
            return "匿名页面解析实际返回了 " + highestHeight +
                    "p 流；VideoFetch 将使用解析器返回的真实流，不会用 1080p 标签伪造画质。";
        }

        if (bilibili && qualities.size() == 1 && qualities.get(0).height <= 480) {
            String accessState = hasSessData ? "已检测到登录凭据" : "当前为匿名访问";
            return "B 站本次只返回了 " + qualities.get(0).height +
                    "p（解析到 " + videoFormatCount + " 个视频格式，" + accessState +
                    "）。匿名请求也可能获取高清画质；实际结果取决于该视频、权限和站点接口，登录不保证解锁更高画质。";
        }
        return "";
    }

    private static String niconicoRouteLabel(String mode) {
        if ("v3-dms".equals(mode)) return "标准 v3 + DMS";
        if ("v3_guest-dms".equals(mode)) return "游客 v3_guest + DMS";
        if ("watchV4-dms".equals(mode)) return "视频页 $watchV4 + DMS";
        if ("server-response-dms".equals(mode)) return "视频页 server-response + DMS";
        if ("v3_guest".equals(mode)) return "游客 v3_guest";
        if ("v3".equals(mode)) return "标准 v3";
        return "未确认的接口路径";
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

    private static boolean hasCookieForHost(File cookieFile, String cookieName, String host) {
        if (cookieFile == null || !cookieFile.isFile() || host == null || host.isEmpty()) return false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(cookieFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || (line.startsWith("#") && !line.startsWith("#HttpOnly_"))) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length < 7 || !cookieName.equals(fields[5]) || fields[6].isEmpty()) continue;

                String domainField = fields[0];
                String domain = domainField.startsWith("#HttpOnly_")
                        ? domainField.substring("#HttpOnly_".length()) : domainField;
                boolean includeSubdomains = domain.startsWith(".");
                String bareDomain = includeSubdomains ? domain.substring(1) : domain;
                if (host.equalsIgnoreCase(bareDomain) ||
                        (includeSubdomains && host.toLowerCase(Locale.ROOT)
                                .endsWith("." + bareDomain.toLowerCase(Locale.ROOT)))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Never log or expose cookie contents.
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
