package com.alfread.autotrigger.shizuku

import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class AlfCommandUserService : IAlfCommandService.Stub() {
    @Volatile private var recorder: Process? = null

    @Keep constructor() : super()

    override fun run(command: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            p.waitFor(8, TimeUnit.SECONDS)
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            if (err.isBlank()) out.trim() else "ERR: ${err.trim()}"
        } catch (t: Throwable) { "ERR: ${t.message}" }
    }

    override fun startRecording(device: String, callback: IAlfRecordCallback) {
        stopRecording()
        Thread {
            try {
                recorder = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getevent -lt $device"))
                BufferedReader(InputStreamReader(recorder!!.inputStream)).use { br ->
                    while (true) {
                        val line = br.readLine() ?: break
                        callback.onLine(line)
                    }
                }
                callback.onStopped("input stream ended")
            } catch (t: Throwable) {
                runCatching { callback.onStopped("${t.message}") }
            } finally {
                runCatching { recorder?.destroy() }
                recorder = null
            }
        }.start()
    }

    override fun stopRecording() {
        runCatching { recorder?.destroy() }
        recorder = null
    }
}
