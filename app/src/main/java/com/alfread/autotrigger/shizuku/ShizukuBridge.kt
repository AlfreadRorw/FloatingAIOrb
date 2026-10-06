package com.alfread.autotrigger.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

object ShizukuBridge {
    private var service: IAlfCommandService? = null
    private var bound = false

    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun hasPermission(): Boolean = runCatching { Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    fun requestPermission(code: Int = 8211) {
        if (!isAvailable()) return
        runCatching { if (!hasPermission()) Shizuku.requestPermission(code) }
    }

    fun bind(onReady: (Boolean) -> Unit) {
        if (!isAvailable() || !hasPermission()) { onReady(false); return }
        val args = UserServiceArgs(ComponentName("com.alfread.autotrigger", "com.alfread.autotrigger.shizuku.AlfCommandUserService"))
            .daemon(true).tag("alf-command").version(2)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = IAlfCommandService.Stub.asInterface(binder)
                bound = service != null
                onReady(bound)
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null; bound = false; onReady(false) }
        }
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { onReady(false) }
    }

    fun ensureBound(onReady: (Boolean) -> Unit) {
        if (service != null) onReady(true) else bind(onReady)
    }

    fun run(command: String): String? = runCatching { service?.run(command) }.getOrNull()

    fun findTouchDevice(): String? {
        val output = run("getevent -lp") ?: return null
        val lines = output.lines()
        var current: String? = null
        var hasX = false
        var hasY = false
        for (line in lines) {
            val add = Regex("add device \\d+: (/dev/input/event\\d+)").find(line)
            if (add != null) { current = add.groupValues[1]; hasX = false; hasY = false }
            if (current != null && line.contains("0035") && line.contains("ABS_MT_POSITION_X")) hasX = true
            if (current != null && line.contains("0036") && line.contains("ABS_MT_POSITION_Y")) hasY = true
            if (current != null && hasX && hasY) return current
        }
        return null
    }

    fun startRecording(device: String, callback: IAlfRecordCallback) { service?.startRecording(device, callback) }
    fun stopRecording() { runCatching { service?.stopRecording() } }
}
