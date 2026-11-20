package com.sxsbsxs.videofetch;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonicalizes pasted/shared video links before they reach yt-dlp.
 *
 * In particular, a malformed value such as https://VIDEO_ID?si=... must never
 * be treated as a DNS hostname. It is a common result of copying only the ID
 * from a shortened YouTube share URL.
 */
public final class VideoUrlNormalizer {
    private static final Pattern HTTP_LINK =
            Pattern.compile("(?i)https?://[^\\s<>\\\"']+");
    private static final Pattern YOUTUBE_LINK_WITHOUT_SCHEME =
            Pattern.compile("(?i)(?:www\\.)?(?:m\\.)?(?:youtube\\.com|youtu\\.be)/[^\\s<>\\\"']+");
    private static final Pattern DOMAIN_LINK_WITHOUT_SCHEME =
            Pattern.compile("(?i)(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(?::[0-9]{1,5})?(?:[/?#][^\\s<>\\\"']*)?");
    private static final Pattern VIDEO_ID =
            Pattern.compile("[A-Za-z0-9_-]{11}");

    private VideoUrlNormalizer() {}

    /**
     * Returns a safe absolute HTTP(S) URL, or null when the input has no valid
     * hostname. Accepts full links, bare YouTube domains, and 11-char video IDs.
     * For share text, the first recognizable URL is extracted.
     */
    public static String normalize(String input) {
        if (input == null) return null;
        String candidate = input.trim();
        if (candidate.isEmpty()) return null;

        Matcher httpMatcher = HTTP_LINK.matcher(candidate);
        if (httpMatcher.find()) {
            candidate = httpMatcher.group();
        } else if (!isVideoIdWithOptionalQuery(candidate)) {
            Matcher youtubeMatcher = YOUTUBE_LINK_WITHOUT_SCHEME.matcher(candidate);
            if (youtubeMatcher.find()) {
                candidate = youtubeMatcher.group();
            } else {
                Matcher domainMatcher = DOMAIN_LINK_WITHOUT_SCHEME.matcher(candidate);
                if (domainMatcher.find()) candidate = domainMatcher.group();
            }
        }

        candidate = trimTrailingPunctuation(candidate);
        if (candidate.isEmpty()) return null;

        if (isVideoIdWithOptionalQuery(candidate)) {
            String id = candidate.substring(0, 11);
            return "https://www.youtube.com/watch?v=" + id;
        }

        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("www.") || lower.startsWith("m.youtube.com/") ||
                lower.startsWith("youtube.com/") || lower.startsWith("youtu.be/")) {
            candidate = "https://" + candidate;
        } else if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            if (!DOMAIN_LINK_WITHOUT_SCHEME.matcher(candidate).matches()) return null;
            candidate = "https://" + candidate;
        }

        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null ||
                    !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }

            // Repair malformed copied links where the video ID was left in the
            // authority/hostname position: https://HJQh7iEnepY?si=...
            if (VIDEO_ID.matcher(host).matches() &&
                    (uri.getRawPath() == null || uri.getRawPath().isEmpty() ||
                     "/".equals(uri.getRawPath()))) {
                return "https://www.youtube.com/watch?v=" + host;
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            boolean hostnameLooksValid = normalizedHost.indexOf('.') > 0 ||
                    normalizedHost.indexOf(':') >= 0 ||
                    "localhost".equals(normalizedHost);
            if (!hostnameLooksValid || normalizedHost.startsWith(".") ||
                    normalizedHost.endsWith(".") || uri.getRawUserInfo() != null) {
                return null;
            }
            return uri.toASCIIString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Returns a privacy-safe source label for diagnostics. It intentionally
     * omits query parameters, fragments, usernames, and video IDs.
     */
    public static String safeSourceLabel(String input) {
        String normalized = normalize(input);
        if (normalized == null) return "无法识别域名";
        try {
            URI uri = new URI(normalized);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return "无法识别域名";
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath();
            String route = "/";
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                String[] parts = path.split("/");
                for (String part : parts) {
                    if (part != null && !part.isEmpty()) {
                        route = "/" + part;
                        break;
                    }
                }
            }
            // Do not expose opaque IDs or arbitrary path parameters.
            return host + route;
        } catch (Exception ignored) {
            return "无法识别域名";
        }
    }

    public static boolean isValidHttpUrl(String input) {
        return normalize(input) != null;
    }

    private static boolean isVideoIdWithOptionalQuery(String value) {
        if (value == null) return false;
        int queryIndex = value.indexOf('?');
        String id = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        if (!VIDEO_ID.matcher(id).matches()) return false;
        if (queryIndex < 0) return true;
        String query = value.substring(queryIndex + 1);
        return !query.isEmpty() && !containsWhitespace(query);
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0) {
            char c = value.charAt(end - 1);
            if (c == '.' || c == ',' || c == ';' || c == '!' ||
                    c == '。' || c == '，' || c == '；' ||
                    c == ')' || c == '）' || c == ']' || c == '】' ||
                    c == '"' || c == '\'') {
                end--;
            } else {
                break;
            }
        }
        return value.substring(0, end);
    }
}
