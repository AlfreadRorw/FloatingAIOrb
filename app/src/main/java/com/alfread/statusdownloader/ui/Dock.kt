package com.alfread.statusdownloader.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alfread.statusdownloader.R

enum class DockTab(val label: String, @DrawableRes val icon: Int) {
    STATUS("Status", R.drawable.ic_nav_status),
    HISTORY("Riwayat", R.drawable.ic_nav_history),
    SETTINGS("Setting", R.drawable.ic_nav_settings)
}

@Composable
fun Dock(selected: DockTab, onSelect: (DockTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 8.dp
        ) {
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTab.values().forEach { tab ->
                    DockItem(tab, tab == selected) { onSelect(tab) }
                }
            }
        }
    }
}

@Composable
private fun DockItem(tab: DockTab, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.background else Color.Transparent, label = "dockBg")
    val fg by animateColorAsState(if (selected) scheme.onBackground else scheme.background, label = "dockFg")
    Row(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = tab.label,
            tint = fg,
            modifier = Modifier.size(24.dp)
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(tab.label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
