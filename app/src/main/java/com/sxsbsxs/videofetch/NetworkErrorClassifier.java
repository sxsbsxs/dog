package com.sxsbsxs.videofetch;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Classifies common transport failures emitted by yt-dlp and its Python runtime. */
public final class NetworkErrorClassifier {
    private NetworkErrorClassifier() {}

    public static boolean isDnsResolutionFailure(String diagnostic) {
        if (diagnostic == null || diagnostic.isEmpty()) return false;
        String value = diagnostic.toLowerCase(java.util.Locale.ROOT);
        return value.contains("no address associated with hostname") ||
                value.contains("temporary failure in name resolution") ||
                value.contains("name or service not known") ||
                value.contains("nodename nor servname provided") ||
                value.contains("getaddrinfo failed") ||
                value.contains("eai_noname") ||
                value.contains("eai_again") ||
                value.contains("failed to resolve") ||
                value.contains("could not resolve host") ||
                value.contains("couldn't resolve host") ||
                value.contains("name resolution");
    }

    /** Includes nested and suppressed exception messages without exposing URL or Cookie data. */
    public static String collectFailureDetails(Throwable failure) {
        if (failure == null) return "";
        StringBuilder result = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendFailure(result, failure, visited, 0);
        return result.toString();
    }

    private static void appendFailure(
            StringBuilder result, Throwable failure, Set<Throwable> visited, int depth
    ) {
        if (failure == null || depth > 12 || !visited.add(failure)) return;
        if (result.length() > 0) result.append('\n');
        result.append(failure.getClass().getSimpleName());
        String message = failure.getMessage();
        if (message != null && !message.trim().isEmpty()) result.append(": ").append(message);
        for (Throwable suppressed : failure.getSuppressed()) {
            appendFailure(result, suppressed, visited, depth + 1);
        }
        Throwable cause = failure.getCause();
        if (cause != null && cause != failure) {
            appendFailure(result, cause, visited, depth + 1);
        }
    }

    public static String dnsFailureMessage() {
        return "DNS 解析失败：手机当前网络无法解析目标网站域名。先用浏览器打开同一个链接；" +
                "如果浏览器也打不开，请切换 Wi-Fi/移动网络，或将系统“私人 DNS”设为“自动”，" +
                "并暂时关闭 VPN、代理或广告拦截 DNS 后重试。";
    }
}
