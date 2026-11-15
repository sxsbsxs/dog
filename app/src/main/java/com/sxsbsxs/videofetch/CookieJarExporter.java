package com.sxsbsxs.videofetch;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exports only cookies belonging to the login site and SSO origins visited by
 * this WebView. It deliberately does not export unrelated cookies from the
 * shared WebView profile.
 */
public final class CookieJarExporter {
    private static final String TAG = "VideoFetchCookieExport";
    private static final long WINDOWS_EPOCH_OFFSET_SECONDS = 11644473600L;

    private CookieJarExporter() {}

    public static String export(Context context, String originUrl) throws IOException {
        Set<String> visited = new LinkedHashSet<>();
        visited.add(originUrl);
        return export(context, originUrl, visited);
    }

    public static String export(
            Context context,
            String originUrl,
            Set<String> visitedUrls
    ) throws IOException {
        String originHost = hostOf(originUrl);
        if (originHost == null) throw new IOException("无法识别登录网站域名，请重新打开授权页面。");

        Set<String> urls = new LinkedHashSet<>();
        if (visitedUrls != null) urls.addAll(visitedUrls);
        urls.add(originUrl);

        Set<String> allowedHosts = new LinkedHashSet<>();
        for (String url : urls) {
            String host = hostOf(url);
            if (host != null) allowedHosts.add(host);
        }

        CookieManager manager = CookieManager.getInstance();
        manager.flush();

        Map<String, String> rows = new LinkedHashMap<>();
        Set<String> fallbackDomains = new LinkedHashSet<>();
        File databaseFile = findCookieDatabase(new File(context.getDataDir(), "app_webview"), 0);
        if (databaseFile != null) {
            readDatabase(databaseFile, rows, fallbackDomains, allowedHosts);
        } else {
            Log.i(TAG, "WebView cookie database unavailable; using CookieManager");
        }

        // CookieManager returns decrypted values, including HttpOnly cookies.
        // Query each actual visited URL so path-scoped login cookies are retained.
        for (String url : urls) {
            addManagerCookies(manager, url, rows, null);
        }

        // If Chromium stored encrypted_value in SQLite, ask CookieManager for
        // the decrypted header and preserve the database cookie's domain scope.
        for (String domain : fallbackDomains) {
            for (String url : urls) {
                String host = hostOf(url);
                if (host != null && cookieDomainCoversHost(domain, host)) {
                    addManagerCookies(manager, url, rows, domain);
                }
            }
        }

        if (rows.isEmpty()) {
            throw new IOException(
                    "没有读取到网页登录 Cookie。请在网页内完成登录，等待页面加载结束后再点“完成授权”。"
            );
        }

        if (!containsCookieForHost(rows.values(), originHost)) {
            throw new IOException(
                    "读到了网页 Cookie，但没有发现属于 " + originHost +
                    " 的登录 Cookie。该网站可能阻止了应用内 WebView 登录，" +
                    "或者登录尚未完成。请回到网页确认登录状态后重试。"
            );
        }

        StringBuilder output = new StringBuilder(
                "# Netscape HTTP Cookie File\n" +
                "# Generated locally by VideoFetch. Do not share this file.\n"
        );
        for (String line : rows.values()) output.append(line).append('\n');
        return output.toString();
    }

    public static int countCookies(String cookieJar) {
        int count = 0;
        if (cookieJar == null) return 0;
        for (String line : cookieJar.split("\n")) {
            if (isCookieRow(line)) count++;
        }
        return count;
    }

    public static int countDomains(String cookieJar) {
        Set<String> domains = new LinkedHashSet<>();
        if (cookieJar == null) return 0;
        for (String line : cookieJar.split("\n")) {
            if (!isCookieRow(line)) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length >= 7) {
                String domain = fields[0].startsWith("#HttpOnly_")
                        ? fields[0].substring("#HttpOnly_".length())
                        : fields[0];
                domains.add(domain.toLowerCase(Locale.ROOT));
            }
        }
        return domains.size();
    }

    private static boolean isCookieRow(String line) {
        return line != null && !line.isEmpty() &&
                (!line.startsWith("#") || line.startsWith("#HttpOnly_"));
    }

    private static File findCookieDatabase(File directory, int depth) {
        if (directory == null || !directory.isDirectory() || depth > 5) return null;
        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && "Cookies".equals(child.getName())) return child;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findCookieDatabase(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void readDatabase(
            File databaseFile,
            Map<String, String> rows,
            Set<String> fallbackDomains,
            Set<String> allowedHosts
    ) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = database.rawQuery("SELECT * FROM cookies", null);

            int hostIndex = cursor.getColumnIndex("host_key");
            int expiryIndex = cursor.getColumnIndex("expires_utc");
            int pathIndex = cursor.getColumnIndex("path");
            int nameIndex = cursor.getColumnIndex("name");
            int valueIndex = cursor.getColumnIndex("value");
            int secureIndex = cursor.getColumnIndex("is_secure");
            int httpOnlyIndex = cursor.getColumnIndex("is_httponly");

            if (hostIndex < 0 || nameIndex < 0 || valueIndex < 0) {
                Log.w(TAG, "WebView cookie database has an unexpected schema");
                return;
            }

            long nowSeconds = System.currentTimeMillis() / 1000L;
            while (cursor.moveToNext()) {
                String domain = cursor.getString(hostIndex);
                String name = cursor.getString(nameIndex);
                String value = cursor.getString(valueIndex);
                if (domain == null || domain.isEmpty() || name == null || name.isEmpty()) continue;
                if (!isRelevantDomain(domain, allowedHosts)) continue;

                long expiry = expiryIndex >= 0 && !cursor.isNull(expiryIndex)
                        ? toUnixSeconds(cursor.getLong(expiryIndex)) : 0L;
                if (expiry > 0L && expiry <= nowSeconds) continue;

                if (value == null || value.isEmpty()) {
                    fallbackDomains.add(domain);
                    continue;
                }

                String path = pathIndex >= 0 ? cursor.getString(pathIndex) : "/";
                if (path == null || path.isEmpty()) path = "/";
                boolean secure = secureIndex >= 0 && cursor.getInt(secureIndex) != 0;
                boolean httpOnly = httpOnlyIndex >= 0 && cursor.getInt(httpOnlyIndex) != 0;
                putCookie(rows, domain, path, secure, expiry, name, value, httpOnly);
            }
        } catch (Exception failure) {
            Log.w(TAG, "Could not read WebView cookie database; using CookieManager", failure);
        } finally {
            if (cursor != null) cursor.close();
            if (database != null) database.close();
        }
    }

    private static boolean isRelevantDomain(String cookieDomain, Set<String> allowedHosts) {
        for (String host : allowedHosts) {
            if (cookieDomainCoversHost(cookieDomain, host)) return true;
        }
        return false;
    }

    private static boolean cookieDomainCoversHost(String cookieDomain, String host) {
        if (cookieDomain == null || host == null) return false;
        String normalized = cookieDomain.toLowerCase(Locale.ROOT);
        boolean includeSubdomains = normalized.startsWith(".");
        String bareDomain = includeSubdomains ? normalized.substring(1) : normalized;
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(bareDomain) ||
                (includeSubdomains && normalizedHost.endsWith("." + bareDomain));
    }

    private static boolean containsCookieForHost(Iterable<String> cookieRows, String host) {
        for (String line : cookieRows) {
            String[] fields = line.split("\t", -1);
            if (fields.length < 7) continue;
            String domain = fields[0].startsWith("#HttpOnly_")
                    ? fields[0].substring("#HttpOnly_".length())
                    : fields[0];
            if (cookieDomainCoversHost(domain, host)) return true;
        }
        return false;
    }

    private static long toUnixSeconds(long storedExpiry) {
        if (storedExpiry <= 0L) return 0L;
        if (storedExpiry > 100000000000L) {
            return storedExpiry / 1000000L - WINDOWS_EPOCH_OFFSET_SECONDS;
        }
        return storedExpiry;
    }

    private static String hostOf(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isEmpty() ||
                    (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addManagerCookies(
            CookieManager manager, String url, Map<String, String> rows, String forcedDomain
    ) {
        String host = hostOf(url);
        if (host == null) return;
        try {
            String header = manager.getCookie(url);
            if (header == null || header.trim().isEmpty()) return;

            Uri uri = Uri.parse(url);
            String domain = forcedDomain == null ? host : forcedDomain;
            String path = "/";
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            for (String part : header.split(";")) {
                String item = part.trim();
                int equals = item.indexOf('=');
                if (equals <= 0) continue;
                String name = item.substring(0, equals).trim();
                String value = item.substring(equals + 1).trim();
                if (name.isEmpty() || value.isEmpty()) continue;
                putCookie(rows, domain, path, secure, 0L, name, value, false);
            }
        } catch (Exception failure) {
            Log.w(TAG, "Could not read cookies for a visited WebView origin", failure);
        }
    }

    private static void putCookie(
            Map<String, String> rows,
            String domain,
            String path,
            boolean secure,
            long expiry,
            String name,
            String value,
            boolean httpOnly
    ) {
        if (domain == null || path == null || name == null || value == null ||
                domain.indexOf('\t') >= 0 || path.indexOf('\t') >= 0 ||
                name.indexOf('\t') >= 0 || value.indexOf('\t') >= 0 ||
                name.indexOf('\n') >= 0 || value.indexOf('\n') >= 0) {
            return;
        }

        boolean includeSubdomains = domain.startsWith(".");
        String cookieDomain = httpOnly ? "#HttpOnly_" + domain : domain;
        String line = cookieDomain + "\t" +
                (includeSubdomains ? "TRUE" : "FALSE") + "\t" +
                path + "\t" +
                (secure ? "TRUE" : "FALSE") + "\t" +
                expiry + "\t" +
                name + "\t" +
                value;
        String key = domain.toLowerCase(Locale.ROOT) + "\n" + path + "\n" + name;
        rows.putIfAbsent(key, line);
    }
}
