package com.alfread.alfvoicecontrol.screen

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alfread.alfvoicecontrol.ui.theme.ALFVoiceControlTheme
import kotlinx.coroutines.delay

/**
 * Carries the actual window flags that turn the display on. It never draws
 * over or interacts with Android's own lock screen UI - if the device is
 * locked with a PIN/pattern/password/biometric, that lock screen still shows
 * on top as normal and the user authenticates with it directly.
 */
class ScreenWakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContent {
            ALFVoiceControlTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ALF woke your screen", style = MaterialTheme.typography.titleMedium)
                }
                LaunchedEffect(Unit) {
                    delay(1200)
                    finish()
                }
            }
        }
    }
}
