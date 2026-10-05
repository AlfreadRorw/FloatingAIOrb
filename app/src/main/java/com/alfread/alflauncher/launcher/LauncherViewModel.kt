package com.alfread.alflauncher.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfread.alflauncher.apps.AppInfo
import com.alfread.alflauncher.apps.AppRepository
import com.alfread.alflauncher.data.HomeLayout
import com.alfread.alflauncher.data.LauncherDataStore
import com.alfread.alflauncher.data.LayoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val store = LauncherDataStore(app)
    private val layoutRepo = LayoutRepository(app)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _settings = MutableStateFlow(com.alfread.alflauncher.data.LauncherSettings())
    val settings: StateFlow<com.alfread.alflauncher.data.LauncherSettings> = _settings.asStateFlow()

    private val _dock = MutableStateFlow<List<String>>(emptyList())
    val dock: StateFlow<List<String>> = _dock.asStateFlow()

    private val _layout = MutableStateFlow(HomeLayout())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { store.settings.collect { _settings.value = it } }
        viewModelScope.launch { store.dock.collect { _dock.value = it } }
        viewModelScope.launch { layoutRepo.layout.collect { _layout.value = it } }
    }

    fun refresh() {
        viewModelScope.launch {
            _apps.value = repo.loadApps()
            val valid = _dock.value.filter { p -> _apps.value.any { it.packageName == p } }
            if (valid != _dock.value) store.saveDock(valid)
            val current = _layout.value
            val cleaned = current.copy(
                dock = current.dock.filter { p -> _apps.value.any { it.packageName == p } },
                items = current.items.filter { it.packageName == null || _apps.value.any { a -> a.packageName == it.packageName } },
                folders = current.folders.map { f ->
                    f.copy(packageNames = f.packageNames.filter { p -> _apps.value.any { a -> a.packageName == p } })
                }.filter { it.packageNames.isNotEmpty() }
            )
            if (cleaned != current) layoutRepo.save(cleaned)
        }
    }

    fun launch(app: AppInfo) = repo.launch(app.packageName)

    fun setDock(packages: List<String>) {
        viewModelScope.launch {
            store.saveDock(packages.take(_settings.value.dockSlots))
            layoutRepo.save(_layout.value.copy(dock = packages.take(_settings.value.dockSlots)))
        }
    }

    fun saveSettings(settings: com.alfread.alflauncher.data.LauncherSettings) {
        viewModelScope.launch { store.save(settings) }
    }

    fun saveLayout(layout: HomeLayout) {
        viewModelScope.launch { layoutRepo.save(layout) }
    }
}
