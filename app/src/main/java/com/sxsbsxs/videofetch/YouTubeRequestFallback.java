package com.sxsbsxs.videofetch;

import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Shared YouTube-specific request policy for metadata inspection and downloads.
 *
 * youtubedl-android 0.18.1 already injects its bundled QuickJS binary into every
 * yt-dlp invocation. We additionally request yt-dlp's EJS scripts from GitHub,
 * then retry with independent clients so extractor-args from one attempt cannot
 * leak into the next.
 */
public final class YouTubeRequestFallback {
    private static final String EJS_REMOTE_COMPONENT = "ejs:github";

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

    /** Enables yt-dlp's current JavaScript challenge components on YouTube requests. */
    public static void applyCommonOptions(YoutubeDLRequest request) {
        request.addOption("--remote-components", EJS_REMOTE_COMPONENT);
        request.addOption("--verbose");
        // Retry transient YouTube webpage/player API connection resets. This
        // does not override an explicit bot challenge or an account restriction.
        request.addOption("--extractor-retries", "5");
    }

    /**
     * Use yt-dlp's current client policy, but exclude web_embedded. In the
     * 2026-11-14 upstream policy, authenticated requests prefer tv_samsung,
     * while guest requests can fall back to visionos. web_embedded is
     * explicitly excluded because YouTube is testing video-bound PO tokens
     * for that client and older ytcfg responses can also be incomplete.
     */
    public static void applyFallback(YoutubeDLRequest request) {
        request.addOption("--extractor-args",
                "youtube:player_client=default,-web_embedded");
    }

    /** Safari's HLS formats can be a useful final route when GVS PO Tokens are unavailable. */
    public static void applyHlsFallback(YoutubeDLRequest request) {
        // Do not include "default" here. With account cookies yt-dlp expands
        // default to web_embedded,tv_samsung,web (or web_creator,...). That
        // re-enables the embedded client, whose config can lack
        // INNERTUBE_CONTEXT and crash _fix_embedded_ytcfg before Safari HLS
        // is tried. This final fallback must be isolated to Safari only.
        request.addOption("--extractor-args", "youtube:player_client=web_safari");
    }

    public static void applyClient(YoutubeDLRequest request, String client) {
        if (!"tv".equals(client) && !"web_safari".equals(client)) {
            throw new IllegalArgumentException("Unsupported YouTube fallback client");
        }
        request.addOption("--extractor-args", "youtube:player_client=" + client);
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

    public static boolean isJavaScriptRuntimeFailure(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("no supported javascript runtime") ||
                normalized.contains("javascript runtime could not be found") ||
                normalized.contains("no js runtime") ||
                normalized.contains("quickjs") && normalized.contains("not found");
    }

    public static boolean isPoTokenFailure(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('-', '_');
        // Do not treat inventory/debug lines such as
        // "PO Token Providers: none" as an extraction failure.
        return normalized.contains("po token is required") ||
                normalized.contains("po token required") ||
                normalized.contains("requires a po token") ||
                normalized.contains("po token was not provided") ||
                normalized.contains("po token which was not provided") ||
                normalized.contains("gvs po token") && normalized.contains("not provided") ||
                normalized.contains("no po_token") ||
                normalized.contains("po_token is required") ||
                normalized.contains("po_token was not provided") ||
                normalized.contains("failed to generate po token") ||
                normalized.contains("could not generate po token");
    }

    public static boolean isEmbeddedConfigFailure(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("keyerror('innertube_context')") ||
                normalized.contains("keyerror: 'innertube_context'");
    }

    public static boolean isHttp403(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("http error 403") ||
                normalized.contains("http 403") ||
                normalized.contains("403 forbidden") ||
                normalized.contains("status code 403") ||
                normalized.contains("error 403");
    }

    public static boolean isTransientNetworkFailure(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("connection reset by peer") ||
                normalized.contains("connection aborted") ||
                normalized.contains("remote end closed connection") ||
                normalized.contains("unexpected eof while reading") ||
                normalized.contains("ssl: eof occurred") ||
                normalized.contains("read timed out") ||
                normalized.contains("connect timed out") ||
                normalized.contains("temporary failure in name resolution") ||
                normalized.contains("network is unreachable") ||
                normalized.contains("transporterror");
    }

    public static boolean isRetryableFailure(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return isBotChallenge(value) || isPoTokenFailure(value) ||
                isHttp403(value) || isEmbeddedConfigFailure(value) ||
                isTransientNetworkFailure(value) ||
                normalized.contains("playability status: login_required") ||
                normalized.contains("login_required");
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
        if (!isYoutubeUrl(url)) return false;
        if (failure != null) {
            Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            Throwable current = failure;
            while (current != null && visited.add(current)) {
                if (isRetryableFailure(current.getMessage()) ||
                        isRetryableFailure(current.toString())) return true;
                for (Throwable suppressed : current.getSuppressed()) {
                    if (isRetryableFailure(suppressed == null ? null : suppressed.toString())) return true;
                }
                current = current.getCause();
            }
        }
        return isRetryableFailure(diagnostics);
    }

    public static String userMessage(String diagnostics) {
        if (isJavaScriptRuntimeFailure(diagnostics)) {
            return "yt-dlp 没有检测到 JavaScript 运行时。VideoFetch 已打包 QuickJS；请检查诊断信息中的 QuickJS 状态，并更新 yt-dlp nightly 后重试。";
        }
        // Prefer the final request's actual bot challenge over older
        // suppressed failures (for example, an earlier embedded-client KeyError).
        if (isBotChallenge(diagnostics)) {
            return "YouTube 拒绝了当前会话或网络出口，要求通过机器人验证。应用已尝试带 Cookie 和不带 Cookie 的 Safari HLS；Cookie 存在不代表会话有效。请在 YouTube 网页确认账号可正常播放，并暂时关闭 VPN 或更换网络出口后重试。";
        }
        if (isEmbeddedConfigFailure(diagnostics)) {
            return "YouTube 的 web_embedded 客户端配置缺少 INNERTUBE_CONTEXT，触发了 yt-dlp 提取器异常。VideoFetch 已改为隔离 Safari HLS 回退，避免再次加载该客户端。";
        }
        if (isPoTokenFailure(diagnostics)) {
            return "YouTube 要求 PO Token。Cookie 和登录状态不能代替它；当前版本会尝试兼容客户端，" +
                    "但尚未配置动态 PO Token Provider，因此部分视频仍可能无法下载。";
        }
        if (isHttp403(diagnostics)) {
            return "YouTube 返回 HTTP 403。可能是媒体请求缺少 PO Token、播放地址过期，或网络出口受到限制；反复登录不一定能解决。";
        }
        if (isTransientNetworkFailure(diagnostics)) {
            return "YouTube 连接被网络对端重置或超时。应用会自动尝试其他客户端；若仍失败，请更换网络或 VPN 出口后再试。";
        }
        return userMessage();
    }

    public static String userMessage() {
        return "YouTube 拦截了当前请求。应用会尝试备用客户端；登录 Cookie 不保证能解除机器人验证或 IP 风控。";
    }
}
