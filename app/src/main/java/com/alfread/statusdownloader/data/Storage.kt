package com.alfread.statusdownloader.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

object Permissions {
    val legacyPermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyPermissions.all {
                ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
            }
        }
}

object StatusRepository {
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
    private val VIDEO_EXT = setOf("mp4", "3gp", "mkv", "webm")

    /** Folder .Statuses: path baru (Android/media) + path lama (Android 10 ke bawah). */
    fun directories(source: WaSource): List<File> {
        val root = Environment.getExternalStorageDirectory() // /storage/emulated/0
        return when (source) {
            WaSource.WHATSAPP -> listOf(
                File(root, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
                File(root, "WhatsApp/Media/.Statuses")
            )
            WaSource.BUSINESS -> listOf(
                File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
                File(root, "WhatsApp Business/Media/.Statuses")
            )
        }
    }

    suspend fun load(sources: Collection<WaSource>): List<StatusItem> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, StatusItem>()
        for (src in sources) {
            for (dir in directories(src)) {
                val files = runCatching { dir.listFiles() }.getOrNull() ?: continue
                for (f in files) {
                    if (!f.isFile || f.length() <= 0L) continue
                    val kind = when (f.extension.lowercase()) {
                        in IMAGE_EXT -> MediaKind.IMAGE
                        in VIDEO_EXT -> MediaKind.VIDEO
                        else -> continue
                    }
                    out[f.absolutePath] = StatusItem(f, kind, f.lastModified(), f.length(), src)
                }
            }
        }
        out.values.toList()
    }
}

sealed interface DownloadResult {
    data class Success(val record: HistoryRecord) : DownloadResult
    data class Failure(val reason: String) : DownloadResult
}

class Downloader(private val context: Context) {

    /** /storage/emulated/0/Download */
    val targetDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    suspend fun download(item: StatusItem, prefix: String, move: Boolean): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val dir = targetDir
                if (!dir.exists() && !dir.mkdirs()) {
                    return@withContext DownloadResult.Failure("Folder Download tidak bisa dibuat")
                }
                if (!item.file.exists()) {
                    return@withContext DownloadResult.Failure("File status sudah dihapus oleh WhatsApp")
                }

                val safePrefix = prefix.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val ext = item.file.extension.ifBlank { if (item.kind == MediaKind.VIDEO) "mp4" else "jpg" }
                val base = item.file.nameWithoutExtension

                var dest = File(dir, "$safePrefix$base.$ext")
                var n = 1
                while (dest.exists()) {
                    dest = File(dir, "$safePrefix${base}_$n.$ext")
                    n++
                }

                try {
                    item.file.copyTo(dest, overwrite = false)
                } catch (e: Exception) {
                    dest.delete()
                    throw e
                }

                if (move) runCatching { item.file.delete() }

                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                    ?: if (item.kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg"
                // Supaya langsung muncul di Galeri
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)

                DownloadResult.Success(
                    HistoryRecord(
                        id = UUID.randomUUID().toString(),
                        originalName = item.file.name,
                        savedPath = dest.absolutePath,
                        kind = item.kind,
                        size = dest.length(),
                        time = System.currentTimeMillis(),
                        origin = item.source.label
                    )
                )
            } catch (e: SecurityException) {
                DownloadResult.Failure("Izin akses file ditolak")
            } catch (e: IOException) {
                DownloadResult.Failure("Gagal menyalin: ${e.message}")
            } catch (e: Exception) {
                DownloadResult.Failure("Terjadi kesalahan: ${e.message}")
            }
        }
}

object Actions {
    private fun uri(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

    private fun mime(file: File, kind: MediaKind): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: if (kind == MediaKind.VIDEO) "video/*" else "image/*"

    fun open(ctx: Context, file: File, kind: MediaKind) {
        if (!file.exists()) {
            Toast.makeText(ctx, "File tidak ditemukan", Toast.LENGTH_SHORT).show(); return
        }
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri(ctx, file), mime(file, kind))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }.onFailure { Toast.makeText(ctx, "Tidak ada aplikasi untuk membuka file", Toast.LENGTH_SHORT).show() }
    }

    fun share(ctx: Context, file: File, kind: MediaKind) {
        if (!file.exists()) {
            Toast.makeText(ctx, "File tidak ditemukan", Toast.LENGTH_SHORT).show(); return
        }
        runCatching {
            val send = Intent(Intent.ACTION_SEND)
                .setType(mime(file, kind))
                .putExtra(Intent.EXTRA_STREAM, uri(ctx, file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "Bagikan lewat"))
        }.onFailure { Toast.makeText(ctx, "Gagal membagikan file", Toast.LENGTH_SHORT).show() }
    }

    fun openWhatsApp(ctx: Context) {
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage("com.whatsapp")
            ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (intent != null) ctx.startActivity(intent)
        else Toast.makeText(ctx, "WhatsApp tidak ditemukan", Toast.LENGTH_SHORT).show()
    }
}
