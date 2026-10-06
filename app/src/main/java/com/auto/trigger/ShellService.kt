package com.auto.trigger
import androidx.annotation.Keep
@Keep
class ShellService : IShellService.Stub() {
    override fun destroy() { System.exit(0) }
    override fun exec(cmd: String): Int = try {
        Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
    } catch (e: Exception) { -1 }
}
