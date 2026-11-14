package com.sxsbsxs.dog;

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
 * Exports this app's WebView cookies into yt-dlp's Netscape cookie format.
 * Database rows preserve exact domain/path/expiry metadata; CookieManager is
 * the fallback for Chromium versions that keep values in encrypted_value.
 */
public final class CookieJarExporter {
    private static final String TAG = "DogCookieExport";
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
        } else {
            Log.i(TAG, "WebView cookie database unavailable; using CookieManager origins");
        }

        // Ask WebView for the decrypted cookie header for every login/redirect
        // origin visited. This also catches cross-domain SSO providers.
        if (visitedUrls != null) {
            for (String url : visitedUrls) {
                addManagerCookies(manager, url, rows);
            }
        }
        addManagerCookies(manager, originUrl, rows);

        // Domain-wide encrypted rows may not have appeared in the visited URL set.
        for (String domain : fallbackDomains) {
            addManagerCookies(manager, "https://" + domain.replaceFirst("^\\.+", "") + "/", rows,
                    domain);
        }

        if (rows.isEmpty()) {
            throw new IOException(
                    "没有读取到登录 Cookie。请确认网页已登录，并在登录成功后停留几秒再点“完成授权”。" +
                    "如果网站禁止应用内登录，请使用该网站支持的登录方式。"
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

                long expiry = expiryIndex >= 0 && !cursor.isNull(expiryIndex)
                        ? toUnixSeconds(cursor.getLong(expiryIndex))
                        : 0L;
                if (expiry > 0L && expiry <= nowSeconds) continue;

                String domain = host;
                domains.add(domain);
                String path = pathIndex >= 0 ? cursor.getString(pathIndex) : "/";
                if (path == null || path.isEmpty()) path = "/";
                boolean secure = secureIndex >= 0 && cursor.getInt(secureIndex) != 0;
                boolean httpOnly = httpOnlyIndex >= 0 && cursor.getInt(httpOnlyIndex) != 0;

                // Modern Chromium can store the actual cookie in encrypted_value.
                if (value == null || value.isEmpty()) {
                    fallbackDomains.add(domain);
                    continue;
                }

                putCookie(rows, domain, path, secure, expiry, name, value, httpOnly);
            }
        } catch (Exception failure) {
            // CookieManager below is independent of the on-disk SQLite schema and
            // can still return decrypted cookies for pages visited in this session.
            Log.w(TAG, "Could not read WebView cookie database; using CookieManager fallback", failure);
        } finally {
            if (cursor != null) cursor.close();
            if (database != null) database.close();
        }
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
            String url,
            Map<String, String> rows
    ) {
        addManagerCookies(manager, url, rows, null);
    }

    private static void addManagerCookies(
            CookieManager manager,
            String url,
            Map<String, String> rows,
            String forcedDomain
    ) {
        if (url == null || url.isEmpty()) return;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isEmpty() ||
                    (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return;
            }

            String header = manager.getCookie(url);
            if (header == null || header.trim().isEmpty()) return;

            String domain = forcedDomain == null ? host : forcedDomain;
            // CookieManager.getCookie() returns no Path metadata. Use "/" so a
            // cookie read from /login remains usable on the site's video URLs.
            // Domain scope stays host-only unless the SQLite row supplied it.
            String path = "/";
            boolean secure = "https".equalsIgnoreCase(scheme);

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
            // Never include cookie values in diagnostic output.
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

        // Prefer the database row: it has exact path/domain/expiry and HttpOnly scope.
        rows.putIfAbsent(key, line);
    }
}
