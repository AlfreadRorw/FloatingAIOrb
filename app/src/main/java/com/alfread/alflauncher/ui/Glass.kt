package com.alfread.alflauncher.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    alpha: Float = .72f,
    radius: Int = 28,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius.dp)
    val base = if (dark) Color.Black else Color.White
    val top = if (dark) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .62f)
    Box(
        modifier = modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = .28f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        top,
                        base.copy(alpha = alpha)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = if (dark) .16f else .35f), shape)
            .graphicsLayer {
                if (Build.VERSION.SDK_INT >= 31) {
                    alpha = 0.999f
                }
            }
    ) { content() }
}
