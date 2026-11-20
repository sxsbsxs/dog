package com.sxsbsxs.videofetch;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps WebView cookies encrypted with a non-exportable Android Keystore key.
 * Older manually entered Cookie headers are retained only for backwards
 * compatibility and are never required by the new login flow.
 */
public final class SecureCookieStore {
    private static final String PREFERENCES = "videofetch_settings";
    private static final String LEGACY_KEY = "cookie";
    private static final String ENCRYPTED_KEY = "cookie_encrypted_v1";
    private static final String COOKIE_JAR_KEY = "cookie_jar_encrypted_v1";
    private static final String COOKIE_SITE_PREFIX = "cookie_site_v2_";
    private static final String KEY_ALIAS = "videofetch_cookie_aes_gcm_v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String COOKIE_FILE_HEADER = "# Netscape HTTP Cookie File";

    private SecureCookieStore() {}

    public static synchronized String load(Context context) {
        SharedPreferences preferences = preferences(context);
        String encrypted = preferences.getString(ENCRYPTED_KEY, null);

        if (encrypted == null || encrypted.isEmpty()) {
            String legacy = preferences.getString(LEGACY_KEY, "");
            if (legacy != null && !legacy.isEmpty()) {
                try {
                    save(context, legacy);
                } catch (Exception ignored) {
                    // Keep the legacy value usable if the device Keystore is temporarily unavailable.
                }
                return legacy;
            }
            return "";
        }

        try {
            return decrypt(encrypted);
        } catch (Exception failure) {
            preferences.edit().remove(ENCRYPTED_KEY).apply();
            return "";
        }
    }

    public static synchronized void save(Context context, String cookie) throws Exception {
        SharedPreferences preferences = preferences(context);
        String value = cookie == null ? "" : cookie.trim();

        if (value.isEmpty()) {
            preferences.edit().remove(ENCRYPTED_KEY).remove(LEGACY_KEY).apply();
            return;
        }

        preferences.edit()
                .putString(ENCRYPTED_KEY, encrypt(value))
                .remove(LEGACY_KEY)
                .apply();
    }

    public static synchronized boolean hasCookieJar(Context context) {
        String cookieJar = loadCookieJar(context);
        if (!cookieJar.startsWith(COOKIE_FILE_HEADER)) return false;
        for (String line : cookieJar.split("\\n")) {
            if (!line.isEmpty() && (!line.startsWith("#") || line.startsWith("#HttpOnly_"))) return true;
        }
        return false;
    }

    public static synchronized String loadCookieJar(Context context) {
        SharedPreferences preferences = preferences(context);
        Map<String, ?> all = preferences.getAll();
        String merged = "";
        boolean hasSiteProfiles = false;

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (!entry.getKey().startsWith(COOKIE_SITE_PREFIX)) continue;
            if (!(entry.getValue() instanceof String)) continue;
            hasSiteProfiles = true;
            try {
                String jar = decrypt((String) entry.getValue());
                merged = mergeCookieJars(merged, jar);
            } catch (Exception failure) {
                // A corrupt profile must not prevent other sites from downloading.
                preferences.edit().remove(entry.getKey()).apply();
            }
        }
        if (hasSiteProfiles) return merged;

        // Read the old single-profile jar only until the next successful site login.
        String encrypted = preferences.getString(COOKIE_JAR_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            return decrypt(encrypted);
        } catch (Exception failure) {
            preferences.edit().remove(COOKIE_JAR_KEY).apply();
            return "";
        }
    }

    /**
     * Saves a pasted Bilibili HTTP Cookie header as a host-scoped Netscape cookie jar.
     * The encrypted profile replaces the existing Bilibili login profile and is only
     * sent to Bilibili domains by yt-dlp's cookie-jar domain matching.
     *
     * @return number of accepted cookie name/value pairs
     */
    public static synchronized int saveBilibiliCookie(Context context, String rawCookie) throws Exception {
        String value = rawCookie == null ? "" : rawCookie.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("请先粘贴 B 站 Cookie");
        }

        // Accept either a raw "name=value; ..." string or the full "Cookie: ..." header.
        value = value.replace("\\r\\n", ";").replace('\\r', ';').replace('\\n', ';').trim();
        if (value.regionMatches(true, 0, "Cookie:", 0, 7)) {
            value = value.substring(7).trim();
        }

        Map<String, String> cookies = new LinkedHashMap<>();
        for (String part : value.split(";")) {
            String pair = part.trim();
            if (pair.regionMatches(true, 0, "Cookie:", 0, 7)) {
                pair = pair.substring(7).trim();
            }
            int separator = pair.indexOf('=');
            if (separator <= 0) continue;

            String name = pair.substring(0, separator).trim();
            String cookieValue = pair.substring(separator + 1).trim();
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) continue;
            if (cookieValue.isEmpty() || cookieValue.indexOf('\\t') >= 0 ||
                    cookieValue.indexOf('\\r') >= 0 || cookieValue.indexOf('\\n') >= 0) continue;
            cookies.put(name, cookieValue);
        }

        if (!cookies.containsKey("SESSDATA")) {
            throw new IllegalArgumentException(
                    "没有检测到 SESSDATA。请粘贴 B 站已登录账号的 Cookie 请求头，而不是单独复制网页地址。");
        }

        StringBuilder jar = new StringBuilder(COOKIE_FILE_HEADER)
                .append("\\n# Bilibili cookies entered locally in VideoFetch.\\n");
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            // Include subdomains so www, api and passport Bilibili endpoints can use
            // the same account session. yt-dlp will not send these rows to other sites.
            jar.append(".bilibili.com\\tTRUE\\t/\\tTRUE\\t0\\t")
                    .append(entry.getKey()).append('\\t')
                    .append(entry.getValue()).append('\\n');
        }

        // Use the same profile key as the in-app Bilibili login page so saving a new
        // Cookie header cleanly replaces the old account session.
        saveCookieJar(context, "https://passport.bilibili.com/login", jar.toString());
        return cookies.size();
    }

    public static synchronized void saveCookieJar(
            Context context, String originUrl, String cookieJar
    ) throws Exception {
        String value = cookieJar == null ? "" : cookieJar.trim();
        if (!value.startsWith(COOKIE_FILE_HEADER) || !containsCookieRows(value)) {
            throw new IllegalArgumentException("没有可保存的有效网页登录 Cookie");
        }

        Uri uri = Uri.parse(originUrl == null ? "" : originUrl);
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("无法识别授权网站，Cookie 未保存");
        }
        String profileKey = profileKeyForHost(host);

        // One independently encrypted profile per login origin. Re-authenticating
        // a site replaces that site's profile without disturbing other sites.
        preferences(context).edit()
                .putString(profileKey, encrypt(value))
                .remove(COOKIE_JAR_KEY)
                .remove(ENCRYPTED_KEY)
                .remove(LEGACY_KEY)
                .apply();
    }


    /** Returns only authorized site names, never cookie values. */
    public static synchronized java.util.List<String> listAuthorizedSites(Context context) {
        java.util.List<String> sites = new java.util.ArrayList<>();
        for (String key : preferences(context).getAll().keySet()) {
            if (!key.startsWith(COOKIE_SITE_PREFIX)) continue;
            String encodedHost = key.substring(COOKIE_SITE_PREFIX.length());
            try {
                String host = new String(
                        Base64.decode(encodedHost, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING),
                        StandardCharsets.UTF_8
                );
                if (!host.isEmpty()) sites.add(host);
            } catch (Exception ignored) {
                // Ignore malformed preference keys without exposing stored values.
            }
        }
        java.util.Collections.sort(sites, String.CASE_INSENSITIVE_ORDER);
        return sites;
    }

    /** Removes one site's encrypted profile without changing other logins. */
    public static synchronized void removeAuthorizedSite(Context context, String host) {
        if (host == null || host.trim().isEmpty()) return;
        preferences(context).edit().remove(profileKeyForHost(host)).apply();
    }

    private static String profileKeyForHost(String host) {
        return COOKIE_SITE_PREFIX +
                Base64.encodeToString(host.trim().toLowerCase(java.util.Locale.ROOT)
                        .getBytes(StandardCharsets.UTF_8),
                        Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String mergeCookieJars(String previous, String incoming) {
        Map<String, String> rows = new LinkedHashMap<>();
        addCookieRows(rows, previous);
        addCookieRows(rows, incoming);

        StringBuilder merged = new StringBuilder(COOKIE_FILE_HEADER)
                .append("\n# Combined locally by VideoFetch. Do not share this file.\n");
        for (String row : rows.values()) {
            merged.append(row).append('\n');
        }
        return merged.toString();
    }

    private static void addCookieRows(Map<String, String> rows, String jar) {
        if (jar == null || jar.isEmpty()) return;
        for (String line : jar.split("\n")) {
            if (line.isEmpty() || (line.startsWith("#") && !line.startsWith("#HttpOnly_"))) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length < 7) continue;

            String domain = fields[0].startsWith("#HttpOnly_")
                    ? fields[0].substring("#HttpOnly_".length())
                    : fields[0];
            String key = domain.toLowerCase(java.util.Locale.ROOT) + "\n" +
                    fields[2] + "\n" + fields[5];
            rows.put(key, line);
        }
    }

    public static synchronized File createTemporaryCookieFile(Context context) throws IOException {
        String cookieJar = loadCookieJar(context);
        if (cookieJar.isEmpty()) return null;

        File cache = context.getCacheDir();
        if (cache == null) {
            throw new IOException("应用缓存目录不可用");
        }
        if (!cache.exists() && !cache.mkdirs()) {
            throw new IOException("无法创建 Cookie 临时目录");
        }

        File temporaryFile = File.createTempFile("videofetch-ytdlp-cookies-", ".txt", cache);
        try {
            // App-private cache files are already sandboxed; also set owner-only mode.
            temporaryFile.setReadable(false, false);
            temporaryFile.setWritable(false, false);
            temporaryFile.setReadable(true, true);
            temporaryFile.setWritable(true, true);
            try (FileOutputStream output = new FileOutputStream(temporaryFile)) {
                output.write(cookieJar.getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }
            return temporaryFile;
        } catch (Exception failure) {
            if (!temporaryFile.delete()) temporaryFile.deleteOnExit();
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("无法写入 Cookie 临时文件", failure);
        }
    }

    public static synchronized void clear(Context context) {
        SharedPreferences preferences = preferences(context);
        SharedPreferences.Editor editor = preferences.edit()
                .remove(COOKIE_JAR_KEY)
                .remove(ENCRYPTED_KEY)
                .remove(LEGACY_KEY);
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(COOKIE_SITE_PREFIX)) editor.remove(key);
        }
        editor.apply();
        CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies(removed -> manager.flush());
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static boolean containsCookieRows(String value) {
        for (String line : value.split("\\n")) {
            if (!line.isEmpty() && (!line.startsWith("#") || line.startsWith("#HttpOnly_"))) return true;
        }
        return false;
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return Base64.encodeToString(payload, Base64.NO_WRAP);
    }

    private static String decrypt(String encoded) throws Exception {
        byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
        if (payload.length <= IV_LENGTH) throw new IllegalStateException("Invalid encrypted Cookie");
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec specification = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(specification);
        return generator.generateKey();
    }
}
