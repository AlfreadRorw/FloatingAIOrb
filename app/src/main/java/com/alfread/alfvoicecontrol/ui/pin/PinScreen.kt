package com.alfread.alfvoicecontrol.ui.pin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alfread.alfvoicecontrol.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreen(onBack: () -> Unit, onPinSaved: () -> Unit) {
    val context = LocalContext.current
    val container = remember { AppContainer.getInstance(context) }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinAlreadySet = remember { container.pinManager.isPinSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create ALF PIN") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "This PIN protects ALF's own settings. It is not a replacement for your " +
                    "phone's lock screen PIN/pattern/password.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
                label = { Text("Create ALF PIN") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 6) confirmPin = it.filter(Char::isDigit) },
                label = { Text("Confirm PIN") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                when {
                    pin.length < 4 -> error = "PIN must be at least 4 digits"
                    pin != confirmPin -> error = "PINs do not match"
                    else -> {
                        container.pinManager.setPin(pin)
                        scope.launch { container.settingsRepository.setPinEnabled(true) }
                        onPinSaved()
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("SAVE PIN") }

            if (pinAlreadySet) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    container.pinManager.clearPin()
                    scope.launch { container.settingsRepository.setPinEnabled(false) }
                    onPinSaved()
                }, modifier = Modifier.fillMaxWidth()) { Text("REMOVE PIN") }
            }
        }
    }
}
