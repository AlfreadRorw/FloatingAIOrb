package com.alfread.alfdownloader.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Shizuku helpers for shell-level Android window management. */
object ShizukuHelper {
    private const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 9321
    private const val FREEFORM_MODE = 5

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
    private fun process(vararg cmd: String): Process? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        method.isAccessible = true
        method.invoke(null, arrayOf(*cmd), null, null) as Process
    }.getOrNull()

    private fun exec(vararg cmd: String): Boolean = runCatching {
        process(*cmd)?.let { p -> p.inputStream.close(); p.errorStream.close(); p.waitFor() == 0 } ?: false
    }.getOrDefault(false)

    private fun execText(command: String): String = runCatching {
        val p = process("sh", "-c", "$command 2>/dev/null") ?: return ""
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        out
    }.getOrDefault("")

    fun whitelistBattery(packageName: String): Boolean =
        exec("sh", "-c", "cmd deviceidle whitelist +$packageName")

    fun allowOverlay(packageName: String): Boolean =
        exec("sh", "-c", "appops set $packageName SYSTEM_ALERT_WINDOW allow")

    /** Enable the Android freeform flags used by several AOSP/OEM builds. */
    fun enableFreeformSupport(): Boolean = exec(
        "sh", "-c",
        "settings put global enable_freeform_support 1; settings put global force_resizable_activities 1"
    )

    /** Launch an actual Android activity as a system freeform task. */
    fun launchFreeform(packageName: String, activityName: String? = null): Boolean {
        val safePkg = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
        if (safePkg.isBlank()) return false
        val component = activityName
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith(".")) "$safePkg$it" else "$safePkg/$it" }
            ?: return exec(
                "sh", "-c",
                "am start --user current --windowingMode $FREEFORM_MODE " +
                    "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $safePkg"
            )
        val safeComponent = component.replace(Regex("[^A-Za-z0-9_.$/]"), "")
        return exec("sh", "-c", "am start --user current --windowingMode $FREEFORM_MODE -n $safeComponent")
    }

    /** Return the current Android task id belonging to a package, if visible. */
    fun findTaskId(packageName: String): Int? {
        val output = execText("dumpsys activity activities")
        output.lineSequence()
            .filter { it.contains("Task{") && it.contains(packageName) }
            .forEach { line ->
                Regex("#(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
            }
        return null
    }

    /** Resize a real Android task. This is a shell API and therefore needs Shizuku. */
    fun resizeTask(taskId: Int, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        if (right <= left || bottom <= top) return false
        val a = exec("sh", "-c", "am task resize $taskId $left $top $right $bottom")
        if (a) return true
        return exec("sh", "-c", "cmd activity task resize $taskId $left $top $right $bottom")
    }
}
