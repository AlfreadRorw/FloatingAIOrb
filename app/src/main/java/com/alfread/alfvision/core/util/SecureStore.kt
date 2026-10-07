package com.alfread.alfvision.core.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted store for the Groq API key.
 *
 * Keystore initialization is deliberately lazy. Some Android/OEM devices can
 * throw from AndroidKeyStore during Application startup; that must never make
 * the whole app crash before the first screen is shown.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)
    private val alias = "alf_vision_secure_key"

    private fun ensureKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            generateKey()
        }
        keyStore.getKey(alias, null) as? SecretKey
    }.recoverCatching {
        // A stale/corrupted OEM keystore entry should not permanently brick
        // API-key storage. Remove it and create a fresh AES-GCM key.
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        runCatching { keyStore.deleteEntry(alias) }
        generateKey()
        val refreshed = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        refreshed.getKey(alias, null) as? SecretKey
    }.getOrNull()

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun putApiKey(value: String) {
        if (value.isBlank()) {
            deleteApiKey()
            return
        }

        runCatching {
            val key = ensureKey() ?: error("Android Keystore is unavailable")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            prefs.edit()
                .putString("api_key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("api_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        }.onFailure {
            // Never crash the UI because secure storage is unavailable.
            deleteApiKey()
        }
    }

    fun getApiKey(): String? {
        val encrypted = prefs.getString("api_key_cipher", null) ?: return null
        val iv = prefs.getString("api_key_iv", null) ?: return null

        return runCatching {
            val key = ensureKey() ?: return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            val bytes = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            String(bytes, StandardCharsets.UTF_8)
        }.getOrElse {
            // If an OEM reset invalidated the old key, discard the unreadable
            // value instead of crashing or repeatedly failing on every launch.
            deleteApiKey()
            null
        }
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun deleteApiKey() {
        prefs.edit()
            .remove("api_key_cipher")
            .remove("api_key_iv")
            .apply()
    }
}
