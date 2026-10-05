package com.alfread.alfvoicecontrol.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * Records short local voice samples used to help the user set up their own
 * commands/wake word. Samples are written to the app's private internal
 * storage only (`filesDir/voice_samples`) and are never sent anywhere.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    var isRecording: Boolean = false
        private set

    private fun samplesDir(): File =
        File(context.filesDir, "voice_samples").apply { if (!exists()) mkdirs() }

    /** Starts recording to a new private file and returns its path. */
    @Throws(IOException::class)
    fun startRecording(fileNameWithoutExtension: String): String {
        stopIfRunning()

        val outFile = File(samplesDir(), "$fileNameWithoutExtension.m4a")
        currentOutputFile = outFile

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outFile.absolutePath)
            prepare()
            start()
        }

        recorder = mediaRecorder
        isRecording = true
        return outFile.absolutePath
    }

    /** Stops recording and returns the saved sample's absolute path, or null if nothing was recording. */
    fun stopRecording(): String? {
        val path = currentOutputFile?.absolutePath
        stopIfRunning()
        return path
    }

    /** Cancels and discards the current in-progress recording. */
    fun cancelRecording() {
        stopIfRunning()
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    fun deleteSample(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun stopIfRunning() {
        if (!isRecording) return
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        isRecording = false
    }
}
