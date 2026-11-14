package com.sxsbsxs.dog;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the one user-supplied Cookie header encrypted with an Android Keystore key.
 * The legacy plaintext preference is migrated on first read.
 */
public final class SecureCookieStore {
    private static final String PREFERENCES = "dog_settings";
    private static final String LEGACY_KEY = "cookie";
    private static final String ENCRYPTED_KEY = "cookie_encrypted_v1";
    private static final String KEY_ALIAS = "dog_cookie_aes_gcm_v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private SecureCookieStore() {}

    public static synchronized String load(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
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
            byte[] payload = Base64.decode(encrypted, Base64.NO_WRAP);
            if (payload.length <= IV_LENGTH) throw new IllegalStateException("Invalid encrypted Cookie");

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            // A reset or restored app backup can leave ciphertext without its Keystore key.
            preferences.edit().remove(ENCRYPTED_KEY).apply();
            return "";
        }
    }

    public static synchronized void save(Context context, String cookie) throws Exception {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String value = cookie == null ? "" : cookie.trim();

        if (value.isEmpty()) {
            preferences.edit().remove(ENCRYPTED_KEY).remove(LEGACY_KEY).apply();
            return;
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

        String encoded = Base64.encodeToString(payload, Base64.NO_WRAP);
        preferences.edit()
                .putString(ENCRYPTED_KEY, encoded)
                .remove(LEGACY_KEY)
                .apply();
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
