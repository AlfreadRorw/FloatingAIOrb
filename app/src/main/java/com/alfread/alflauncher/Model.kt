package com.alfread.alflauncher

import android.app.Application
import android.content.*
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppInfo(val pkg: String, val cls: String, val label: String, val icon: ImageBitmap)

class LauncherVM(private val app: Application) : AndroidViewModel(app) {
    private val p = app.getSharedPreferences("alf", Context.MODE_PRIVATE)
    private fun list(k: String) = p.getString(k, "")!!.split("|").filter { it.isNotEmpty() }

    val apps = mutableStateListOf<AppInfo>()
    val home = mutableStateListOf<String>().apply { addAll(list("home")) }
    val dock = mutableStateListOf<String>().apply { addAll(list("dock")) }
    var dockCount by mutableIntStateOf(p.getInt("dockCount", 5))
    var opacity by mutableFloatStateOf(p.getFloat("opacity", 0.28f))
    var theme by mutableStateOf(p.getString("theme", "system")!!)
    var h24 by mutableStateOf(p.getBoolean("h24", true))
    var dim by mutableFloatStateOf(p.getFloat("dim", 0.15f))

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refresh()
    }

    init {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    override fun onCleared() { runCatching { app.unregisterReceiver(receiver) } }

    fun save() {
        p.edit().putString("home", home.joinToString("|")).putString("dock", dock.joinToString("|"))
            .putInt("dockCount", dockCount).putFloat("opacity", opacity).putString("theme", theme)
            .putBoolean("h24", h24).putFloat("dim", dim).apply()
    }

    fun byPkg(pkg: String) = apps.firstOrNull { it.pkg == pkg }

    fun refresh() {
        viewModelScope.launch {
            val found = withContext(Dispatchers.Default) { load() }
            apps.clear(); apps.addAll(found)
            val ok = found.map { it.pkg }.toSet()
            home.retainAll(ok); dock.retainAll(ok)
            if (!p.getBoolean("init", false) && found.isNotEmpty()) {
                val pm = app.packageManager
                listOf(
                    Intent(Intent.ACTION_DIAL), Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                ).forEach { i ->
                    val pkg = runCatching { pm.resolveActivity(i, 0)?.activityInfo?.packageName }.getOrNull()
                    if (pkg != null && pkg in ok && pkg !in dock) dock.add(pkg)
                }
                p.edit().putBoolean("init", true).apply()
            }
            save()
        }
    }

    private fun load(): List<AppInfo> {
        val pm = app.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).mapNotNull { ri ->
            runCatching {
                val ai = ri.activityInfo
                if (ai.packageName == app.packageName) null
                else AppInfo(ai.packageName, ai.name, ri.loadLabel(pm).toString(),
                    ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap())
            }.getOrNull()
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    fun launch(a: AppInfo) {
        runCatching {
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.onFailure { refresh() }
    }

    fun uninstall(a: AppInfo) {
        runCatching {
            app.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun addHome(a: AppInfo) { if (a.pkg !in home) { home.add(a.pkg); save() } }
    fun addDock(a: AppInfo): Boolean {
        if (a.pkg in dock) return true
        if (dock.size >= dockCount) return false
        dock.add(a.pkg); save(); return true
    }
    fun removeHome(a: AppInfo) { home.remove(a.pkg); save() }
    fun removeDock(a: AppInfo) { dock.remove(a.pkg); save() }
    fun moveDock(a: AppInfo, dir: Int) {
        val i = dock.indexOf(a.pkg); val j = i + dir
        if (i >= 0 && j in dock.indices) { dock.removeAt(i); dock.add(j, a.pkg); save() }
    }
}
