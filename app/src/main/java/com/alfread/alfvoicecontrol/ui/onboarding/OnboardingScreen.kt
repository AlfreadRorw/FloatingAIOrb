package com.alfread.alfvoicecontrol.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

private data class OnboardingStep(val title: String, val description: String)

private val steps = listOf(
    OnboardingStep("Microphone", "ALF needs microphone access to hear your wake word and commands."),
    OnboardingStep("Notifications", "ALF shows a notification whenever it's actively listening."),
    OnboardingStep("Voice setup", "You'll record your own voice for each command you create."),
    OnboardingStep("Wake word", "Choose the word ALF listens for before it accepts a command."),
    OnboardingStep("Optional ALF PIN", "Protect ALF's own settings with a PIN if you'd like."),
    OnboardingStep("Background operation", "Allow ALF to keep listening even with the screen off.")
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var stepIndex by remember { mutableIntStateOf(-1) } // -1 = welcome screen

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { stepIndex += 1 }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { stepIndex += 1 }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (stepIndex == -1) {
            Text("Welcome to ALF Voice Control", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "Control your phone with your voice.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = { stepIndex = 0 }) { Text("GET STARTED") }
        } else if (stepIndex < steps.size) {
            val step = steps[stepIndex]
            Text(step.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(step.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Button(onClick = {
                when (stepIndex) {
                    0 -> {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) stepIndex += 1 else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    1 -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) stepIndex += 1 else notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            stepIndex += 1
                        }
                    }
                    else -> stepIndex += 1
                }
            }) { Text("CONTINUE") }
        } else {
            Text("ALF Voice Control is ready.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Say: \"Alf\"", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onFinished) { Text("FINISH") }
        }
    }
}
