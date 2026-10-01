package com.alfread.statusdownloader.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.alfread.statusdownloader.R
import com.alfread.statusdownloader.data.MediaKind
import com.alfread.statusdownloader.data.StatusItem

@Composable
fun PreviewOverlay(
    item: StatusItem,
    downloaded: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (item.kind) {
            MediaKind.IMAGE -> AsyncImage(
                model = item.file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            MediaKind.VIDEO -> VideoPlayer(item.file.absolutePath)
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = Color.White)
            }
            IconButton(onClick = onDownload, enabled = !busy && !downloaded) {
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White
                    )
                    downloaded -> Icon(Icons.Default.Check, contentDescription = "Sudah diunduh", tint = Color.White)
                    else -> Icon(
                        painterResource(R.drawable.ic_download),
                        contentDescription = "Unduh",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(path: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            VideoView(ctx).apply {
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoPath(path)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                }
            }
        },
        onRelease = { it.stopPlayback() }
    )
}
