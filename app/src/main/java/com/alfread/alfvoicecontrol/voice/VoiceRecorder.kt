package com.alfread.alfvoicecontrol.voice

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(commandId: String): File {
        stopSilently()
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val file = File(dir, "$commandId-${System.currentTimeMillis()}.m4a")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(128_000)
            newRecorder.setAudioSamplingRate(44_100)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
        }.getOrElse {
            newRecorder.release()
            file.delete()
            throw IllegalStateException("Recording could not start: ${it.message}", it)
        }
        recorder = newRecorder
        currentFile = file
        return file
    }

    fun stop(): File? {
        val localRecorder = recorder ?: return currentFile
        val file = currentFile
        recorder = null
        currentFile = null
        runCatching { localRecorder.stop() }
        localRecorder.reset()
        localRecorder.release()
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    fun stopSilently() {
        val file = runCatching { stop() }.getOrNull()
        if (file != null && file.length() == 0L) file.delete()
    }

    fun delete(filePath: String?) {
        filePath?.let { File(it).delete() }
    }

    fun play(filePath: String, onFinished: () -> Unit = {}) {
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(filePath)
            player.setOnCompletionListener { it.release(); onFinished() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); onFinished(); true }
            player.prepare()
            player.start()
        }.onFailure {
            player.release()
            onFinished()
        }
    }
}
