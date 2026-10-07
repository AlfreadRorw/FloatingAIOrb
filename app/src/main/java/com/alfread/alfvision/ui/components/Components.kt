package com.alfread.alfvision.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.ui.theme.AlfCyan
import com.alfread.alfvision.ui.theme.brandBrush

// ---------------------------------------------------------------------------------------------
// DOCK BAR (floating pill navigation). Dipakai di app utama dan di dalam panel overlay.
// ---------------------------------------------------------------------------------------------

data class DockItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun DockBar(
    items: List<DockItem>,
    selectedRoute: String?,
    onSelect: (DockItem) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(if (compact) 22.dp else 30.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = if (compact) 6.dp else 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                DockButton(item, item.route == selectedRoute, compact) { onSelect(item) }
            }
        }
    }
}

@Composable
private fun DockButton(item: DockItem, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "dockBackground")
    val foreground by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "dockForeground")
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 11.dp, vertical = if (compact) 8.dp else 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = item.label, tint = foreground, modifier = Modifier.size(if (compact) 20.dp else 22.dp))
        AnimatedVisibility(visible = selected) {
            Row {
                Spacer(Modifier.width(6.dp))
                Text(item.label, color = foreground, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// CARDS & TEXT
// ---------------------------------------------------------------------------------------------

@Composable
fun AlfCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.4.sp
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun StatusTile(
    icon: ImageVector,
    title: String,
    value: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    AlfCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint)
                Spacer(Modifier.weight(1f))
                Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconBadge(icon, tint, size = 44)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val brush = if (enabled) brandBrush(accent) else Brush.linearGradient(listOf(Color(0xFF555B7A), Color(0xFF3F4466)))
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        if (onDismiss != null) IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 64)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// FORM ROWS (dipakai Settings dan tab Settings di panel overlay)
// ---------------------------------------------------------------------------------------------

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Slider yang hanya menyimpan nilai ketika jari dilepas. Sebelumnya setiap pixel geseran
 * menulis ke DataStore + Room sehingga UI patah-patah.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { "%.2f".format(it) },
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format(local), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = local, onValueChange = { local = it }, valueRange = range, onValueChangeFinished = { onCommit(local) })
    }
}

@Composable
fun BrandDot(modifier: Modifier = Modifier, size: Int = 36) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, AlfCyan))),
        contentAlignment = Alignment.Center,
        content = {}
    )
}
