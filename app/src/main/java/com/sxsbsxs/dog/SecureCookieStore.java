package com.sxsbsxs.dog;

import android.content.Context;
import android.content.SharedPreferences;
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
    private static final String PREFERENCES = "dog_settings";
    private static final String LEGACY_KEY = "cookie";
    private static final String ENCRYPTED_KEY = "cookie_encrypted_v1";
    private static final String COOKIE_JAR_KEY = "cookie_jar_encrypted_v1";
    private static final String KEY_ALIAS = "dog_cookie_aes_gcm_v1";
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
            if (!line.isEmpty() && !line.startsWith("#")) return true;
        }
        return false;
    }

    public static synchronized String loadCookieJar(Context context) {
        String encrypted = preferences(context).getString(COOKIE_JAR_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            return decrypt(encrypted);
        } catch (Exception failure) {
            preferences(context).edit().remove(COOKIE_JAR_KEY).apply();
            return "";
        }
    }

    public static synchronized void saveCookieJar(Context context, String cookieJar) throws Exception {
        String value = cookieJar == null ? "" : cookieJar.trim();
        if (!value.startsWith(COOKIE_FILE_HEADER) || !containsCookieRows(value)) {
            throw new IllegalArgumentException("没有可保存的有效网页登录 Cookie");
        }

        // Once a new website login is saved, remove the old unrestricted Cookie header.
        String previous = loadCookieJar(context);
        String merged = mergeCookieJars(previous, value);
        preferences(context).edit()
                .putString(COOKIE_JAR_KEY, encrypt(merged))
                .remove(ENCRYPTED_KEY)
                .remove(LEGACY_KEY)
                .apply();
    }

    private static String mergeCookieJars(String previous, String incoming) {
        Map<String, String> rows = new LinkedHashMap<>();
        addCookieRows(rows, previous);
        addCookieRows(rows, incoming);

        StringBuilder merged = new StringBuilder(COOKIE_FILE_HEADER)
                .append("\n# Combined locally by Dog. Do not share this file.\n");
        for (String row : rows.values()) {
            merged.append(row).append('\n');
        }
        return merged.toString();
    }

    private static void addCookieRows(Map<String, String> rows, String jar) {
        if (jar == null || jar.isEmpty()) return;
        for (String line : jar.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
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

        File temporaryFile = File.createTempFile("dog-ytdlp-cookies-", ".txt", cache);
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
        preferences(context).edit()
                .remove(COOKIE_JAR_KEY)
                .remove(ENCRYPTED_KEY)
                .remove(LEGACY_KEY)
                .apply();
        CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies(removed -> manager.flush());
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static boolean containsCookieRows(String value) {
        for (String line : value.split("\\n")) {
            if (!line.isEmpty() && !line.startsWith("#")) return true;
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
