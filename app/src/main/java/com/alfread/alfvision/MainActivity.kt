package com.alfread.alfvision

import android.app.Activity
import android.content.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            val intent = Intent(this, ProjectionService::class.java).apply {
                putExtra(ProjectionService.EXTRA_RESULT_CODE, r.resultCode)
                putExtra(ProjectionService.EXTRA_DATA, r.data)
            }
            startForegroundService(intent)
            ensureOverlayThenStart()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppScreen() }
    }

    override fun onResume() { super.onResume() }

    private fun ensureOverlayThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Aktifkan tampil di atas aplikasi lain, lalu kembali dan tekan Start Panel", Toast.LENGTH_LONG).show()
            return
        }
        startService(Intent(this, OverlayService::class.java))
    }

    private fun startCapturePermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    @Composable
    private fun AppScreen() {
        var key by remember { mutableStateOf(AppPrefs.getApiKey(this) ?: "") }
        var model by remember { mutableStateOf(AppPrefs.getModel(this)) }
        var prompt by remember { mutableStateOf(AppPrefs.getSystemPrompt(this)) }
        var saved by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(AppPrefs.getHistory(this)) }

        MaterialTheme(colorScheme = darkColorScheme(
            background = Color(0xFF07080A),
            surface = Color(0xFF101217),
            primary = Color(0xFF9B7BFF),
            onPrimary = Color.White,
            onBackground = Color(0xFFF5F5F7)
        )) {
            LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("ALF Vision Panel", fontSize = 28.sp, color = Color.White)
                    Text("Floating AI yang bisa melihat area layar yang kamu pilih", color = Color(0xFF9EA3AF))
                }
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF101217))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Groq API", color = Color.White, fontSize = 18.sp)
                            OutlinedTextField(key, { key=it }, modifier=Modifier.fillMaxWidth(), label={Text("API Key")}, singleLine=true)
                            Text("Key disimpan terenkripsi memakai Android Keystore.", color=Color(0xFF969AA4), fontSize=12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick={ AppPrefs.setApiKey(this@MainActivity,key); saved=true }) { Text("Simpan") }
                                if(saved) Text("Tersimpan", modifier=Modifier.padding(top=12.dp), color=Color(0xFFB7FFCF), fontSize=12.sp)
                            }
                        }
                    }
                }
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF101217))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Vision model", color = Color.White, fontSize = 18.sp)
                            ModelChoice(model, "qwen/qwen3.8-27b") { model=it; AppPrefs.setModel(this@MainActivity,it) }
                            ModelChoice(model, "qwen/qwen3.6-27b") { model=it; AppPrefs.setModel(this@MainActivity,it) }
                        }
                    }
                }
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF101217))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("AI behavior", color=Color.White, fontSize=18.sp)
                            OutlinedTextField(prompt,{prompt=it},modifier=Modifier.fillMaxWidth(),minLines=5,label={Text("System prompt")})
                            Button(onClick={AppPrefs.setSystemPrompt(this@MainActivity,prompt)}) { Text("Simpan instruksi") }
                        }
                    }
                }
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF101217))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Floating panel", color=Color.White, fontSize=18.sp)
                            Text("Butuh izin tampil di atas aplikasi lain dan screen capture.",color=Color(0xFF9EA3AF),fontSize=12.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={startCapturePermission()}) { Text("Start Panel") }
                                OutlinedButton(onClick={ startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }) { Text("Overlay") }
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()) {
                        Text("Riwayat", color=Color.White, fontSize=18.sp)
                        TextButton(onClick={AppPrefs.clearHistory(this@MainActivity); history=emptyList()}) { Text("Hapus") }
                    }
                }
                items(history.take(15)) { item ->
                    ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=Color(0xFF101217))) {
                        Column(Modifier.padding(14.dp)) { Text(item.first,color=Color.White); Spacer(Modifier.height(6.dp)); Text(item.second,color=Color(0xFFB4B7C0),fontSize=13.sp) }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModelChoice(current:String, value:String, onClick:()->Unit){ Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ RadioButton(selected=current==value,onClick=onClick); Column{Text(value,color=Color.White); Text(if(value.contains("3.8"))"Recommended for vision + JSON" else "Alternative vision model",color=Color(0xFF9EA3AF),fontSize=12.sp)} } }
}
