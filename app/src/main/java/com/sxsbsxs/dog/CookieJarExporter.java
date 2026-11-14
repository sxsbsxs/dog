package com.sxsbsxs.dog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.webkit.CookieManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Exports cookies from the app's own Android WebView profile to yt-dlp's
 * Netscape cookie-file format. Cookie values are never logged.
 */
public final class CookieJarExporter {
    private static final String TAG = "DogCookieExport";
    private static final long WINDOWS_EPOCH_OFFSET_SECONDS = 11644473600L;

    private CookieJarExporter() {}

    public static String export(Context context, String originUrl) throws IOException {
        CookieManager manager = CookieManager.getInstance();
        manager.flush();

        Map<String, String> rows = new LinkedHashMap<>();
        Set<String> domains = new LinkedHashSet<>();
        Set<String> fallbackDomains = new LinkedHashSet<>();
        File databaseFile = new File(
                context.getDataDir(),
                "app_webview/Default/Cookies"
        );

        if (databaseFile.isFile()) {
            readDatabase(databaseFile, rows, domains, fallbackDomains);
        }

        // Android's CookieManager can return decrypted values for rows where
        // Chromium stores the bytes in encrypted_value instead of value.
        for (String domain : fallbackDomains) {
            addManagerCookies(manager, domain, rows);
        }
        if (!hasDatabaseCookieForOrigin(domains, originUrl)) {
            addManagerCookies(manager, originUrl, rows);
        }

        if (rows.isEmpty()) {
            throw new IOException(
                    "没有读取到登录 Cookie。请先在网页中完成登录，等页面加载完成后再点“完成授权”。"
            );
        }

        StringBuilder output = new StringBuilder(
                "# Netscape HTTP Cookie File\n" +
                "# Generated locally by Dog. Do not share this file.\n"
        );
        for (String line : rows.values()) {
            output.append(line).append('\n');
        }
        return output.toString();
    }

    private static void readDatabase(
            File databaseFile,
            Map<String, String> rows,
            Set<String> domains,
            Set<String> fallbackDomains
    ) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );
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
                String host = cursor.getString(hostIndex);
                String name = cursor.getString(nameIndex);
                String value = cursor.getString(valueIndex);
                if (host == null || host.isEmpty() || name == null || name.isEmpty()) continue;

                String domain = host;

                long expiry = expiryIndex >= 0 && !cursor.isNull(expiryIndex)
                        ? toUnixSeconds(cursor.getLong(expiryIndex))
                        : 0L;
                if (expiry > 0L && expiry <= nowSeconds) continue;
                domains.add(domain);

                String path = pathIndex >= 0 ? cursor.getString(pathIndex) : "/";
                if (path == null || path.isEmpty()) path = "/";
                boolean secure = secureIndex >= 0 && cursor.getInt(secureIndex) != 0;
                boolean httpOnly = httpOnlyIndex >= 0 && cursor.getInt(httpOnlyIndex) != 0;

                // An empty database value can mean WebView stored the actual bytes
                // in encrypted_value. CookieManager fallback below fills these rows.
                if (value == null || value.isEmpty()) {
                    fallbackDomains.add(domain);
                    continue;
                }

                putCookie(rows, domain, path, secure, expiry, name, value, httpOnly);
            }
        } catch (Exception failure) {
            Log.w(TAG, "Could not read WebView cookie database; falling back to CookieManager", failure);
        } finally {
            if (cursor != null) cursor.close();
            if (database != null) database.close();
        }
    }

    private static boolean hasDatabaseCookieForOrigin(Set<String> domains, String originUrl) {
        try {
            String host = Uri.parse(originUrl).getHost();
            if (host == null || host.isEmpty()) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : domains) {
                String normalized = domain.toLowerCase(Locale.ROOT).replaceFirst("^\\.+", "");
                if (host.equals(normalized) || host.endsWith("." + normalized) ||
                        normalized.endsWith("." + host)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // The caller's origin fallback will handle malformed URLs.
        }
        return false;
    }

    private static long toUnixSeconds(long storedExpiry) {
        if (storedExpiry <= 0L) return 0L;
        // Chromium stores expires_utc as microseconds since 1601-01-01.
        if (storedExpiry > 100000000000L) {
            return storedExpiry / 1000000L - WINDOWS_EPOCH_OFFSET_SECONDS;
        }
        return storedExpiry;
    }

    private static void addManagerCookies(
            CookieManager manager,
            String hostOrUrl,
            Map<String, String> rows
    ) {
        if (hostOrUrl == null || hostOrUrl.isEmpty()) return;
        boolean suppliedUrl = hostOrUrl.contains("://");
        String url = hostOrUrl;
        if (!suppliedUrl) {
            url = "https://" + hostOrUrl.replaceFirst("^\\.+", "") + "/";
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return;

            String header = manager.getCookie(url);
            if (header == null || header.trim().isEmpty()) return;

            // Preserve Chromium's leading dot for domain cookies; it controls subdomain scope.
            String domain = suppliedUrl ? host : hostOrUrl;
            for (String part : header.split(";")) {
                String item = part.trim();
                int equals = item.indexOf('=');
                if (equals <= 0) continue;
                String name = item.substring(0, equals).trim();
                String value = item.substring(equals + 1).trim();
                if (name.isEmpty() || value.isEmpty()) continue;
                putCookie(
                        rows,
                        domain,
                        "/",
                        "https".equalsIgnoreCase(uri.getScheme()),
                        0L,
                        name,
                        value,
                        false
                );
            }
        } catch (Exception failure) {
            Log.w(TAG, "Could not read cookies for a WebView origin", failure);
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
        if (domain.indexOf('\t') >= 0 || path.indexOf('\t') >= 0 ||
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
        rows.put(key, line);
    }
}
