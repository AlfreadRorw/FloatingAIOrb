package com.alfread.alflauncher.dock

data class DockState(
    val packageNames: List<String> = emptyList(),
    val slots: Int = 5
)
