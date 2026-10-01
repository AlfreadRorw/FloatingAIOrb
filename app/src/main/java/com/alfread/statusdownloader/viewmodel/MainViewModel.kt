package com.alfread.statusdownloader.viewmodel

import android.app.Application
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfread.statusdownloader.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val historyStore = HistoryStore(app)
    private val downloader = Downloader(app)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _history = MutableStateFlow(historyStore.load())
    val history: StateFlow<List<HistoryRecord>> = _history.asStateFlow()

    private val _ui = MutableStateFlow(StatusUiState())
    val ui: StateFlow<StatusUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val targetPath: String get() = downloader.targetDir.absolutePath

    private var loadJob: Job? = null

    // ---------- Izin & muat data ----------
    fun checkPermission() {
        val ok = Permissions.hasAccess(getApplication<Application>())
        _ui.update { it.copy(hasPermission = ok) }
        if (ok) refresh()
    }

    fun refresh() {
        if (!_ui.value.hasPermission) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val s = _settings.value
            val sources = if (s.includeBusiness) WaSource.values().toList() else listOf(WaSource.WHATSAPP)
            val loaded = StatusRepository.load(sources)
            val list = if (s.newestFirst) loaded.sortedByDescending { it.modified }
            else loaded.sortedBy { it.modified }
            val keys = list.map { it.key }.toSet()
            _ui.update { st ->
                st.copy(loading = false, items = list, selected = st.selected.intersect(keys))
            }
        }
    }

    // ---------- Filter & seleksi ----------
    fun setFilter(f: StatusFilter) = _ui.update { it.copy(filter = f) }

    fun toggleSelect(key: String) = _ui.update { st ->
        st.copy(selected = if (key in st.selected) st.selected - key else st.selected + key)
    }

    fun clearSelection() = _ui.update { it.copy(selected = emptySet()) }

    fun selectAll(keys: List<String>) = _ui.update { it.copy(selected = keys.toSet()) }

    // ---------- Pengaturan ----------
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = transform(old)
        _settings.value = new
        settingsStore.save(new)
        if (old.includeBusiness != new.includeBusiness || old.newestFirst != new.newestFirst) refresh()
    }

    // ---------- Unduh ----------
    private fun isDownloaded(item: StatusItem) =
        _history.value.any { it.originalName == item.file.name }

    fun download(item: StatusItem) {
        if (isDownloaded(item)) {
            _messages.tryEmit("Status ini sudah pernah diunduh"); return
        }
        viewModelScope.launch {
            val err = saveOne(item)
            _messages.tryEmit(err ?: "Tersimpan di folder Download & Galeri")
        }
    }

    fun downloadSelected() {
        val st = _ui.value
        val targets = st.items.filter { it.key in st.selected }
        if (targets.isEmpty()) return
        _ui.update { it.copy(selected = emptySet()) }
        viewModelScope.launch {
            var ok = 0
            var skipped = 0
            var failed = 0
            for (item in targets) {
                if (isDownloaded(item)) { skipped++; continue }
                if (saveOne(item) == null) ok++ else failed++
            }
            _messages.tryEmit(buildString {
                append("$ok berhasil diunduh")
                if (skipped > 0) append(", $skipped dilewati")
                if (failed > 0) append(", $failed gagal")
            })
        }
    }

    /** @return null jika sukses, atau pesan error */
    private suspend fun saveOne(item: StatusItem): String? {
        _ui.update { it.copy(busy = it.busy + item.key) }
        val s = _settings.value
        val result = downloader.download(item, s.prefix, s.moveMode)
        _ui.update { it.copy(busy = it.busy - item.key) }
        return when (result) {
            is DownloadResult.Success -> {
                val updated = listOf(result.record) + _history.value
                _history.value = updated
                historyStore.save(updated)
                if (s.moveMode) {
                    _ui.update { st -> st.copy(items = st.items.filterNot { it.key == item.key }) }
                }
                null
            }
            is DownloadResult.Failure -> result.reason
        }
    }

    // ---------- Riwayat ----------
    fun deleteHistory(record: HistoryRecord, deleteFile: Boolean) {
        if (deleteFile) removeFiles(listOf(record.savedPath))
        val updated = _history.value.filterNot { it.id == record.id }
        _history.value = updated
        historyStore.save(updated)
    }

    fun clearHistory(deleteFiles: Boolean) {
        if (deleteFiles) removeFiles(_history.value.map { it.savedPath })
        _history.value = emptyList()
        historyStore.save(emptyList())
    }

    private fun removeFiles(paths: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { runCatching { File(it).delete() } }
            MediaScannerConnection.scanFile(
                getApplication<Application>(), paths.toTypedArray(), null, null
            )
        }
    }
}
