package com.alfread.alfvision.core.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

class ShizukuCompat(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    init { refresh() }

    fun refresh() {
        _available.value = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        _permissionGranted.value = if (_available.value) {
            runCatching { Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        } else false
    }

    fun requestPermission(requestCode: Int) {
        if (_available.value && !_permissionGranted.value) runCatching { Shizuku.requestPermission(requestCode) }
    }
}
