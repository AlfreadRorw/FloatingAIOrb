package com.alfread.alflauncher.data

data class HomeItem(
    val id: String,
    val type: String,
    val packageName: String? = null,
    val folderId: String? = null,
    val page: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0
)

data class HomeFolder(
    val id: String,
    val name: String,
    val packageNames: List<String> = emptyList()
)

data class HomeLayout(
    val items: List<HomeItem> = emptyList(),
    val folders: List<HomeFolder> = emptyList(),
    val dock: List<String> = emptyList(),
    val pages: Int = 1
)
