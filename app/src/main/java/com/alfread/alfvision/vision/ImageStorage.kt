package com.alfread.alfvision.vision

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageStorage(private val context: Context) {
    private val dir by lazy { File(context.filesDir, "vision_images").apply { mkdirs() } }

    fun save(bytes: ByteArray, prefix: String = "capture"): String {
        val file = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    fun read(path: String): ByteArray? = runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull()

    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    fun delete(path: String?) { path?.let { File(it).delete() } }
}
