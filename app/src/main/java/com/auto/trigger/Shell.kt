package com.auto.trigger
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

object Shell {
    @Volatile var svc: IShellService? = null
    private var binding = false
    fun available() = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    fun granted() = available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    fun ready() = svc != null
    fun bind(ctx: Context) {
        if (svc != null || binding || !granted()) return
        binding = true
        val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name))
            .daemon(false).processNameSuffix("shell").version(1)
        Shizuku.bindUserService(args, object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, b: IBinder?) { svc = IShellService.Stub.asInterface(b); binding = false }
            override fun onServiceDisconnected(n: ComponentName?) { svc = null; binding = false }
        })
    }
    fun run(cmd: String) { try { svc?.exec(cmd) } catch (_: Exception) {} }
}
