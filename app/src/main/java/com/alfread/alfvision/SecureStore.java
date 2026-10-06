package com.alfread.alfvision;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String PREFS = "alf_secure";
    private static final String KEY_NAME = "alf_vision_key";
    private static final String VALUE = "groq_api_key";

    private SecureStore() {}

    private static SecretKey getKey() throws Exception {
        android.security.keystore.KeyStore ks =
                android.security.keystore.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_NAME)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        return ((SecretKey) ks.getKey(KEY_NAME, null));
    }

    public static void put(Context context, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] combined = new byte[cipher.getIV().length + encrypted.length];
            System.arraycopy(cipher.getIV(), 0, combined, 0, cipher.getIV().length);
            System.arraycopy(encrypted, 0, combined, cipher.getIV().length, encrypted.length);
            prefs(context).edit().putString(VALUE, Base64.encodeToString(combined, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {
        }
    }

    public static String get(Context context) {
        try {
            String encoded = prefs(context).getString(VALUE, "");
            if (encoded.isEmpty()) return "";
            byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
