package com.alfread.alfvision

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AppPrefs {
    private const val PREF = "alf_vision_prefs"
    private const val KEY_API = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_SYSTEM = "system_prompt"
    private const val KEY_REGION = "region"
    private const val KEY_HISTORY = "history"
    private const val KEY_INTERVAL = "auto_interval"
    private const val KS = "AndroidKeyStore"
    private const val KEY_ALIAS = "ALF_VISION_AES"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getModel(ctx: Context): String = prefs(ctx).getString(KEY_MODEL, "qwen/qwen3.8-27b") ?: "qwen/qwen3.8-27b"
    fun setModel(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_MODEL, value).apply()
    fun getSystemPrompt(ctx: Context): String = prefs(ctx).getString(KEY_SYSTEM, "You are a practical Android screen assistant. Analyze the provided screenshot region carefully, explain what is visible, and answer the user's question directly. Do not claim to see anything outside the image.") ?: ""
    fun setSystemPrompt(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_SYSTEM, value).apply()
    fun getInterval(ctx: Context): Long = prefs(ctx).getLong(KEY_INTERVAL, 0L)
    fun setInterval(ctx: Context, value: Long) = prefs(ctx).edit().putLong(KEY_INTERVAL, value).apply()

    fun getApiKey(ctx: Context): String? = runCatching {
        val s = prefs(ctx).getString(KEY_API, null) ?: return null
        val blob = Base64.decode(s, Base64.DEFAULT)
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val data = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), StandardCharsets.UTF_8)
    }.getOrNull()

    fun setApiKey(ctx: Context, value: String) {
        if (value.isBlank()) {
            prefs(ctx).edit().remove(KEY_API).apply()
            return
        }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val blob = byteArrayOf(iv.size.toByte()) + iv + encrypted
        prefs(ctx).edit().putString(KEY_API, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", KS)
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    fun saveRegion(ctx: Context, l: Int, t: Int, r: Int, b: Int) {
        prefs(ctx).edit().putString(KEY_REGION, "$l,$t,$r,$b").apply()
    }
    fun getRegion(ctx: Context): IntArray {
        val v = prefs(ctx).getString(KEY_REGION, "80,360,1000,1200") ?: "80,360,1000,1200"
        return v.split(',').mapNotNull { it.toIntOrNull() }.let { a ->
            if (a.size == 4) intArrayOf(a[0], a[1], a[2], a[3]) else intArrayOf(80,360,1000,1200)
        }
    }

    fun addHistory(ctx: Context, question: String, answer: String) {
        val old = runCatching { JSONArray(prefs(ctx).getString(KEY_HISTORY, "[]")) }.getOrElse { JSONArray() }
        val obj = JSONObject().put("q", question).put("a", answer).put("ts", System.currentTimeMillis())
        old.put(obj)
        while (old.length() > 50) old.remove(0)
        prefs(ctx).edit().putString(KEY_HISTORY, old.toString()).apply()
    }

    fun getHistory(ctx: Context): List<Pair<String,String>> {
        val arr = runCatching { JSONArray(prefs(ctx).getString(KEY_HISTORY, "[]")) }.getOrElse { JSONArray() }
        val out = mutableListOf<Pair<String,String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += o.optString("q") to o.optString("a")
        }
        return out.asReversed()
    }

    fun clearHistory(ctx: Context) = prefs(ctx).edit().remove(KEY_HISTORY).apply()
}
