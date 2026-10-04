package com.alfread.alfdownloader.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Pembungkus tipis di atas Shizuku-API. Semua eksekusi shell dilakukan lewat refleksi
 * terhadap Shizuku.newProcess, supaya kalau API berubah di versi lain, fitur ini gagal
 * dengan aman (dikembalikan false) tanpa membuat seluruh aplikasi gagal dibangun.
 *
 * Dipakai untuk hal-hal yang TIDAK butuh root dan memang didukung lewat shell biasa
 * (uid "shell"): whitelist Doze/baterai, memberi izin overlay tanpa buka Pengaturan,
 * dan (eksperimental) membuka aplikasi lain dalam mode jendela bebas (freeform) kalau
 * perangkatnya mendukung.
 */
object ShizukuHelper {
    private const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 9321

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun isReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        isReady() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private var pendingListener: Shizuku.OnRequestPermissionResultListener? = null

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!isReady()) { onResult(false); return }
        if (hasPermission()) { onResult(true); return }
        pendingListener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
        val listener = Shizuku.OnRequestPermissionResultListener { code, grant ->
            if (code == REQUEST_CODE) {
                pendingListener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
                pendingListener = null
                onResult(grant == PackageManager.PERMISSION_GRANTED)
            }
        }
        pendingListener = listener
        runCatching {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure { onResult(false) }
    }

    @Suppress("DiscouragedPrivateApi")
    private fun exec(vararg cmd: String): Boolean = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf(*cmd), null, null) as Process
        process.waitFor() == 0
    }.getOrDefault(false)

    /** Keluarkan paket dari pembatasan Doze/baterai agar tidak dimatikan sistem. */
    fun whitelistBattery(packageName: String): Boolean =
        exec("sh", "-c", "cmd deviceidle whitelist +$packageName")

    /** Beri izin "Tampil di atas aplikasi lain" tanpa membuka halaman Pengaturan. */
    fun allowOverlay(packageName: String): Boolean =
        exec("sh", "-c", "appops set $packageName SYSTEM_ALERT_WINDOW allow")

    /**
     * Eksperimental: minta sistem membuka sebuah paket dalam mode jendela bebas (freeform).
     * Hasilnya sangat bergantung dukungan perangkat/ROM — banyak HP non-tablet menolak
     * permintaan ini walau perintahnya berhasil terkirim.
     */
    fun launchFreeform(packageName: String): Boolean = exec(
        "sh", "-c",
        "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
            "-p $packageName --windowingMode 5"
    )
}
