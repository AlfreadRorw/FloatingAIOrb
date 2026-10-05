package com.alfread.alflauncher.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.layoutStore by preferencesDataStore("alf_layout")

class LayoutRepository(private val context: Context) {
    private val itemsKey = stringPreferencesKey("items")
    private val foldersKey = stringPreferencesKey("folders")
    private val dockKey = stringPreferencesKey("dock")
    private val pagesKey = stringPreferencesKey("pages")

    val layout: Flow<HomeLayout> = context.layoutStore.data.map { p ->
        val items = runCatching {
            val a = JSONArray(p[itemsKey] ?: "[]")
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(HomeItem(
                        id = o.getString("id"),
                        type = o.getString("type"),
                        packageName = o.optString("packageName").ifBlank { null },
                        folderId = o.optString("folderId").ifBlank { null },
                        page = o.optInt("page", 0),
                        cellX = o.optInt("cellX", 0),
                        cellY = o.optInt("cellY", 0)
                    ))
                }
            }
        }.getOrDefault(emptyList())

        val folders = runCatching {
            val a = JSONArray(p[foldersKey] ?: "[]")
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val pkgs = o.optJSONArray("packages")
                    add(HomeFolder(
                        id = o.getString("id"),
                        name = o.optString("name", "Folder"),
                        packageNames = buildList {
                            if (pkgs != null) for (j in 0 until pkgs.length()) add(pkgs.getString(j))
                        }
                    ))
                }
            }
        }.getOrDefault(emptyList())

        HomeLayout(
            items = items,
            folders = folders,
            dock = (p[dockKey] ?: "").split("|").filter(String::isNotBlank),
            pages = (p[pagesKey] ?: "1").toIntOrNull()?.coerceAtLeast(1) ?: 1
        )
    }

    suspend fun save(layout: HomeLayout) {
        context.layoutStore.edit { p ->
            p[itemsKey] = JSONArray().apply {
                layout.items.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("type", item.type)
                        put("packageName", item.packageName ?: "")
                        put("folderId", item.folderId ?: "")
                        put("page", item.page)
                        put("cellX", item.cellX)
                        put("cellY", item.cellY)
                    })
                }
            }.toString()

            p[foldersKey] = JSONArray().apply {
                layout.folders.forEach { folder ->
                    put(JSONObject().apply {
                        put("id", folder.id)
                        put("name", folder.name)
                        put("packages", JSONArray(folder.packageNames))
                    })
                }
            }.toString()

            p[dockKey] = layout.dock.joinToString("|")
            p[pagesKey] = layout.pages.toString()
        }
    }
}
