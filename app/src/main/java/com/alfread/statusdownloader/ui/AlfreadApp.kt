package com.alfread.statusdownloader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.alfread.statusdownloader.data.Actions
import com.alfread.statusdownloader.data.StatusItem
import com.alfread.statusdownloader.data.DeletedMessage
import com.alfread.statusdownloader.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@Composable
fun AlfreadApp(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val history by vm.history.collectAsState()
    val deletedMessages by vm.deletedMessages.collectAsState()

    var tab by rememberSaveable { mutableStateOf(DockTab.STATUS) }
    var preview by remember { mutableStateOf<StatusItem?>(null) }
    val snack = remember { SnackbarHostState() }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        vm.messages.collectLatest { msg ->
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(msg)
        }
    }

    BackHandler(enabled = ui.selected.isNotEmpty() && preview == null) { vm.clearSelection() }
    BackHandler(enabled = tab != DockTab.STATUS && ui.selected.isEmpty() && preview == null) {
        tab = DockTab.STATUS
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = scheme.background,
            snackbarHost = {
                SnackbarHost(snack) { data ->
                    Snackbar(data, containerColor = scheme.onBackground, contentColor = scheme.background)
                }
            },
            bottomBar = { Dock(selected = tab, onSelect = { tab = it }) }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                Crossfade(targetState = tab, label = "tabs") { current ->
                    when (current) {
                        DockTab.STATUS -> StatusScreen(
                            ui = ui,
                            settings = settings,
                            history = history,
                            onGrantPermission = onRequestPermission,
                            onRefresh = vm::refresh,
                            onFilter = vm::setFilter,
                            onToggleSelect = vm::toggleSelect,
                            onClearSelection = vm::clearSelection,
                            onSelectAll = vm::selectAll,
                            onDownload = vm::download,
                            onDownloadSelected = vm::downloadSelected,
                            onPreview = { preview = it },
                            onOpenWhatsApp = { Actions.openWhatsApp(ctx) }
                        )
                        DockTab.DELETED -> DeletedMessagesScreen(
                            messages = deletedMessages,
                            notificationAccess = vm.hasNotificationAccess(),
                            onOpenNotificationSettings = { vm.openNotificationSettings(ctx) },
                            onDelete = vm::deleteDeletedMessage,
                            onClear = vm::clearDeletedMessages
                        )
                        DockTab.HISTORY -> HistoryScreen(
                            history = history,
                            onOpen = { Actions.open(ctx, File(it.savedPath), it.kind) },
                            onShare = { Actions.share(ctx, File(it.savedPath), it.kind) },
                            onDelete = vm::deleteHistory,
                            onClear = vm::clearHistory
                        )
                        DockTab.SETTINGS -> SettingsScreen(
                            settings = settings,
                            hasPermission = ui.hasPermission,
                            targetPath = vm.targetPath,
                            historyCount = history.size,
                            onChange = vm::updateSettings,
                            onManagePermission = onRequestPermission,
                            onClearHistory = { vm.clearHistory(false) }
                        )
                    }
                }
            }
        }

        preview?.let { item ->
            PreviewOverlay(
                item = item,
                downloaded = history.any { it.originalName == item.file.name },
                busy = item.key in ui.busy,
                onClose = { preview = null },
                onDownload = {
                    vm.download(item)
                    if (settings.moveMode) preview = null
                },
                onShare = { Actions.share(ctx, item.file, item.kind) }
            )
        }
    }
}
