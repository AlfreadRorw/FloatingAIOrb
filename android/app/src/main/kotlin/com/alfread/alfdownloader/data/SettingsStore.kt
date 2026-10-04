package com.alfread.alfdownloader.data

import android.content.Context
import android.content.SharedPreferences
import com.alfread.alfdownloader.model.Prefs
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Penyimpanan pengaturan.
 *
 * Perbaikan bug "fav hilang": sebelumnya layar utama menyimpan SELURUH objek Prefs miliknya
 * (salinan lama) sehingga menimpa daftar fav yang baru disimpan bar mengambang → kembali ke
 * default (hanya TikTok). Sekarang:
 *  1) fav disimpan di kunci terpisah ("pinned_v2") yang TIDAK pernah ditimpa oleh save() biasa,
 *  2) semua perubahan lewat update{} yang membaca nilai TERBARU dulu (atomik, satu kunci global).
 */
class SettingsStore(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("alf_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val pinSerializer = ListSerializer(String.serializer())

    companion object {
        private val lock = Any()
        private const val KEY_PREFS = "prefs"
        private const val KEY_PINNED = "pinned_v2"
    }

    fun load(): Prefs = synchronized(lock) { loadLocked() }

    private fun loadLocked(): Prefs {
        val base = runCatching {
            val raw = sp.getString(KEY_PREFS, null)
            if (raw == null) Prefs() else json.decodeFromString(Prefs.serializer(), raw)
        }.getOrDefault(Prefs())
        return base.copy(pinnedPackages = readPinned(base.pinnedPackages))
    }

    private fun readPinned(fallback: List<String>): List<String> {
        val raw = sp.getString(KEY_PINNED, null)
        if (raw == null) {          // migrasi sekali dari versi lama
            writePinned(fallback)
            return fallback
        }
        return runCatching { json.decodeFromString(pinSerializer, raw) }.getOrDefault(fallback)
    }

    private fun writePinned(list: List<String>) {
        sp.edit().putString(KEY_PINNED, json.encodeToString(pinSerializer, list.distinct())).apply()
    }

    private fun writePrefs(prefs: Prefs) {
        sp.edit().putString(KEY_PREFS, json.encodeToString(Prefs.serializer(), prefs)).apply()
    }

    /** Simpan. Daftar fav sengaja TIDAK ikut ditulis di sini — ubah fav lewat update{}. */
    fun save(prefs: Prefs) = synchronized(lock) { writePrefs(prefs) }

    /** Baca nilai terbaru → ubah → simpan, secara atomik. Mengembalikan hasil akhir. */
    fun update(block: (Prefs) -> Prefs): Prefs = synchronized(lock) {
        val old = loadLocked()
        val next = block(old)
        writePrefs(next)
        if (next.pinnedPackages != old.pinnedPackages) writePinned(next.pinnedPackages)
        next
    }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)
}
