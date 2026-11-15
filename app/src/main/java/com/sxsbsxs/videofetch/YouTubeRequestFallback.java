package com.sxsbsxs.videofetch;

import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * A narrowly-scoped retry for YouTube's "not a bot" gate.
 *
 * Keep yt-dlp's default client order as the first choice (it generally offers
 * the best format coverage). Only after the known bot challenge do we retry
 * with clients that can expose alternate, sometimes HLS-backed formats.
 */
public final class YouTubeRequestFallback {
    public static final String FALLBACK_EXTRACTOR_ARGS =
            "youtube:player_client=tv,web_safari";

    private YouTubeRequestFallback() {}

    public static boolean isYoutubeUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            String host = new URI(value.trim()).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("youtube.com") || host.endsWith(".youtube.com") ||
                    host.equals("youtu.be");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isBotChallenge(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'')
                .replace("\\u0027", "'");
        return normalized.contains("sign in to confirm you're not a bot") ||
                normalized.contains("confirm you're not a bot") ||
                normalized.contains("not a bot");
    }

    public static boolean isBotChallenge(Throwable failure) {
        if (failure == null) return false;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (isBotChallenge(current.getMessage()) ||
                    isBotChallenge(current.toString())) return true;
            for (Throwable suppressed : current.getSuppressed()) {
                if (isBotChallenge(suppressed)) return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean shouldRetry(String url, Throwable failure, String diagnostics) {
        return isYoutubeUrl(url) &&
                (isBotChallenge(failure) || isBotChallenge(diagnostics));
    }

    public static void applyFallback(YoutubeDLRequest request) {
        request.addOption("--extractor-args", FALLBACK_EXTRACTOR_ARGS);
    }

    public static String userMessage() {
        return "YouTube 拦截了当前会话或网络请求。请先在“网站登录与 Cookie”登录 YouTube，" +
                "也可导入浏览器导出的 cookies.txt；应用已尝试备用客户端。若仍失败，可能是网络出口/IP 风控，Cookie 不保证能解除限制。";
    }
}
