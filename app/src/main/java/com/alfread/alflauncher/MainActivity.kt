package com.alfread.alflauncher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val vm: LauncherVM by viewModels()
    private var homeSignal = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { LauncherApp(vm, homeSignal.intValue) { requestDefault() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal.intValue++
    }

    private fun requestDefault() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}
