package com.alfread.alfdownloader.shizuku

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku

/** Hasil percobaan membuka aplikasi sebagai jendela freeform. */
enum class FreeformResult { FREEFORM, NOT_FREEFORM, FAILED }

/** Shizuku helpers for shell-level Android window management. */
object ShizukuHelper {
    private const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 9321
    private const val FREEFORM_MODE = 5

    /** State yang bisa diamati Compose — otomatis ter-update saat Shizuku start/stop. */
    val binderAlive = mutableStateOf(false)
    val granted = mutableStateOf(false)

    private var inited = false

    /** Panggil sekali (MainActivity & OverlayService). Aman dipanggil berulang. */
    @Synchronized
    fun init() {
        if (!inited) {
            inited = true
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener { refresh() })
                Shizuku.addBinderDeadListener(Shizuku.OnBinderDeadListener { refresh() })
                Shizuku.addRequestPermissionResultListener(
                    Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
                )
            }
        }
        refresh()
    }

    fun refresh() {
        binderAlive.value = isReady()
        granted.value = hasPermission()
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    }.getOrDefault(false)

    fun isReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        isReady() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** True bila pengguna pernah memilih "Tolak & jangan tanya lagi" — izin harus diberi manual di Shizuku. */
    fun permanentlyDenied(): Boolean = runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

    fun openShizukuApp(context: Context): Boolean = runCatching {
        val i = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i); true
    }.getOrDefault(false)

    private var pendingListener: Shizuku.OnRequestPermissionResultListener? = null

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!isReady()) { onResult(false); return }
        if (hasPermission()) { refresh(); onResult(true); return }
        pendingListener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
        val listener = Shizuku.OnRequestPermissionResultListener { code, grant ->
            if (code == REQUEST_CODE) {
                pendingListener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
                pendingListener = null
                refresh()
                onResult(grant == PackageManager.PERMISSION_GRANTED)
            }
        }
        pendingListener = listener
        runCatching {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure { onResult(false) }
    }

    // ------------------------------------------------------------------ shell

    @Suppress("DiscouragedPrivateApi")
    private fun process(vararg cmd: String): Process? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        method.isAccessible = true
        method.invoke(null, arrayOf(*cmd), null, null) as Process
    }.getOrNull()

    /** Jalankan perintah shell (uid shell) dan kembalikan (exitCode, output gabungan stdout+stderr). */
    private fun sh(command: String): Pair<Int, String> = runCatching {
        val p = process("sh", "-c", "$command 2>&1") ?: return -1 to ""
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor() to out
    }.getOrDefault(-1 to "")

    private fun ok(command: String): Boolean = sh(command).first == 0

    fun whitelistBattery(packageName: String): Boolean = ok("cmd deviceidle whitelist +$packageName")

    /** Beri ALF izin "Run commands in Termux" tanpa membuka Pengaturan Android. */
    fun grantTermuxRunCommand(alfPackage: String): Boolean =
        ok("pm grant $alfPackage com.termux.permission.RUN_COMMAND")

    fun allowOverlay(packageName: String): Boolean = ok("appops set $packageName SYSTEM_ALERT_WINDOW allow")

    /** Aktifkan flag freeform yang dipakai banyak ROM AOSP/OEM. Beberapa ROM butuh restart. */
    fun enableFreeformSupport(): Boolean = ok(
        "settings put global enable_freeform_support 1; " +
            "settings put global force_resizable_activities 1; " +
            "settings put global freeform_window_management 1"
    )

    fun freeformFlags(context: Context): Pair<Boolean, Boolean> {
        val r = context.contentResolver
        val a = runCatching { android.provider.Settings.Global.getInt(r, "enable_freeform_support", 0) == 1 }.getOrDefault(false)
        val b = runCatching { android.provider.Settings.Global.getInt(r, "force_resizable_activities", 0) == 1 }.getOrDefault(false)
        return a to b
    }

    /** Buka aplikasi sebagai task freeform asli Android. */
    fun launchFreeform(packageName: String, activityName: String? = null): Boolean {
        val safePkg = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
        if (safePkg.isBlank()) return false
        val cmd = if (!activityName.isNullOrBlank()) {
            val comp = (if (activityName.startsWith(".")) "$safePkg/$safePkg$activityName" else "$safePkg/$activityName")
                .replace(Regex("[^A-Za-z0-9_.$/]"), "")
            "am start --user current --windowingMode $FREEFORM_MODE -n $comp"
        } else {
            "am start --user current --windowingMode $FREEFORM_MODE " +
                "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $safePkg"
        }
        val (code, out) = sh(cmd)
        // `am start` kadang tetap exit 0 walau gagal; cek teks error.
        return code == 0 && !out.contains("Error", ignoreCase = true) && !out.contains("Exception")
    }

    data class TaskInfo(val id: Int, val mode: String?)

    /** Cari task milik paket dan windowing mode-nya (mis. "freeform", "fullscreen"). */
    fun findTask(packageName: String): TaskInfo? {
        val (_, output) = sh("dumpsys activity activities")
        var best: TaskInfo? = null
        for (line in output.lineSequence()) {
            if (!line.contains("Task{") || !line.contains(packageName)) continue
            val id = Regex("#(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
            val mode = Regex("mode=(\\w+)").find(line)?.groupValues?.getOrNull(1)
            val info = TaskInfo(id, mode)
            if (best == null || mode == "freeform") best = info
            if (mode == "freeform") break
        }
        return best
    }

    fun findTaskId(packageName: String): Int? = findTask(packageName)?.id

    /** Resize task asli Android. Butuh Shizuku (shell API). */
    fun resizeTask(taskId: Int, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        if (right <= left || bottom <= top) return false
        return ok("am task resize $taskId $left $top $right $bottom") ||
            ok("cmd activity task resize $taskId $left $top $right $bottom")
    }

    /** Paksa task ke freeform bila sebelumnya terbuka fullscreen. */
    fun setTaskFreeform(taskId: Int): Boolean =
        ok("am task set-windowing-mode $taskId $FREEFORM_MODE") ||
            ok("cmd activity task set-windowing-mode $taskId $FREEFORM_MODE")
}
