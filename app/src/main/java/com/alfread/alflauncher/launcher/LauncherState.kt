package com.alfread.alflauncher.launcher

data class LauncherState(
    val currentPage: Int = 0,
    val pageCount: Int = 1,
    val editMode: Boolean = false,
    val drawerOpen: Boolean = false
)
