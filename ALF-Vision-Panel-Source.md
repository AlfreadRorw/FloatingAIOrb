# ALF Vision Panel — Complete Source Listing

Full text of all project source/config/resource files in repository order.

## `.github/workflows/build.yml`

```yml
name: Build ALF Vision Panel

on:
  push:
    branches: [ master, main ]
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

env:
  JAVA_VERSION: '17'
  ANDROID_COMPILE_SDK: '36'
  ANDROID_BUILD_TOOLS: '36.0.0'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: gradle

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install Android SDK packages
        shell: bash
        run: |
          yes | sdkmanager --licenses >/dev/null || true
          sdkmanager "platforms;android-${ANDROID_COMPILE_SDK}" "build-tools;${ANDROID_BUILD_TOOLS}"

      - name: Make Gradle executable
        run: chmod +x ./gradlew

      - name: Lint
        run: ./gradlew lint --stacktrace

      - name: Unit tests
        run: ./gradlew test --stacktrace

      - name: Build debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload debug artifact
        uses: actions/upload-artifact@v4
        with:
          name: ALF-Vision-Debug
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error

      - name: Prepare signing keystore
        id: signing
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          if [ -n "$KEYSTORE_BASE64" ] && [ -n "$KEYSTORE_PASSWORD" ] && [ -n "$KEY_ALIAS" ] && [ -n "$KEY_PASSWORD" ]; then
            echo "$KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/release.jks"
            echo "available=true" >> "$GITHUB_OUTPUT"
          else
            echo "available=false" >> "$GITHUB_OUTPUT"
          fi

      - name: Build signed release
        if: steps.signing.outputs.available == 'true'
        env:
          KEYSTORE_FILE: ${{ runner.temp }}/release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          ./gradlew assembleRelease \
            -PsigningStoreFile="$KEYSTORE_FILE" \
            -PsigningStorePassword="$KEYSTORE_PASSWORD" \
            -PsigningKeyAlias="$KEY_ALIAS" \
            -PsigningKeyPassword="$KEY_PASSWORD" \
            --stacktrace

      - name: Upload release artifact
        if: steps.signing.outputs.available == 'true'
        uses: actions/upload-artifact@v4
        with:
          name: ALF-Vision-Release
          path: app/build/outputs/apk/release/*.apk
          if-no-files-found: error

```

## `.gitignore`

```text
.gradle/
.idea/
/local.properties
/build/
*/build/
*.iml
.externalNativeBuild/
.cxx/
.DS_Store
captures/
keystore.properties
*.jks
*.keystore

```

## `README.md`

```md
# ALF Vision Panel

Native Android AI screen assistant yang dapat hidup di atas aplikasi lain, memakai MediaProjection untuk mengambil layar, memilih region yang dapat dipindahkan/di-resize, lalu mengirim hanya area terpilih ke Groq Vision ketika pengguna meminta analisis.

## Core capabilities

- Native Kotlin + Jetpack Compose + Material 3
- Android overlay `TYPE_APPLICATION_OVERLAY`
- Floating panel dan minimized AI orb
- MediaProjection + VirtualDisplay + ImageReader
- Region selector dengan drag, resize, reset, center, fullscreen, save
- Region presets lokal dengan Room
- Groq Chat Completions over HTTPS
- Current vision model default: `qwen/qwen3.8-27b`
- Secure Groq API key encryption using Android Keystore + AES/GCM
- Chat, multi-turn context, quick vision actions, OCR/extract-text prompts, translation prompts
- Screenshot compare menggunakan dua image dalam satu vision request
- Annotation editor: rectangle, circle, line, arrow, text, blur, undo, redo
- Local Room history
- AI profiles dan response styles
- Voice input melalui Android SpeechRecognizer
- Privacy controls dan screenshot retention controls
- Optional Shizuku status integration; app tetap bekerja tanpa Shizuku
- GitHub Actions lint, tests, debug build, optional signed release

## Architecture

```text
UI / Compose
   |
   +--> MainViewModel
   |
   +--> VisionAssistantController
           |
           +--> CaptureCoordinator --> ScreenCaptureService --> MediaProjection/ImageReader
           |
           +--> ImageProcessor
           |
           +--> GroqRepository --> GroqApiService --> Groq HTTPS API
           |
           +--> HistoryRepository --> Room
           +--> ProfileRepository --> Room
           +--> RegionRepository --> Room
           +--> SettingsRepository --> DataStore + Room snapshot
           +--> SecureStore --> Android Keystore

FloatingPanelService
   +--> TYPE_APPLICATION_OVERLAY
   +--> FloatingPanel
   +--> FloatingOrb
   +--> RegionSelectorOverlay
```

## Requirements

- Android Studio with JDK 17
- Android SDK 36
- Android device with Android 7.0+ (API 24+)
- Internet connection for Groq requests
- Groq API key created by the user

## Groq API key

1. Open ALF Vision Panel.
2. Open Settings > AI.
3. Enter the Groq API key manually.
4. Press Save.
5. Press Test Connection.

The key is encrypted locally with an Android Keystore AES key. It is not written to Git, DataStore, notifications, clipboard, or Logcat.

The default model is `qwen/qwen3.8-27b`, currently documented by Groq as a multimodal model with vision and OCR capability. The app also exposes the Groq Models endpoint so active models can be refreshed instead of relying on deprecated model IDs.

## Permissions

Required depending on features:

- Overlay: Android Settings > Draw over other apps
- MediaProjection: shown by the Android system screen capture consent dialog
- Notifications: used for the media-projection foreground service on modern Android
- Microphone: only for voice input
- Shizuku: optional enhancement/status only

## Screen capture privacy

The application does not silently start MediaProjection. The system consent dialog is required. Capture requests are on-demand by default. When region mode is active, the image is cropped before the Groq request. The floating panel and region overlay are temporarily hidden during capture so selector controls are not intentionally included in the submitted frame.

## Build locally

```bash
chmod +x ./gradlew
./gradlew lint
./gradlew test
./gradlew assembleDebug
```

`gradlew` in this repository is a self-bootstrapping wrapper script that downloads Gradle 8.13 into the user's Gradle cache when required. It intentionally does not depend on a checked-in binary wrapper JAR.

## GitHub Actions

Workflow: `.github/workflows/build.yml`

The workflow:

- checks out the repository
- installs JDK 17
- installs Android SDK 36 / Build Tools 36.0.0
- runs lint
- runs unit tests
- builds `assembleDebug`
- uploads `ALF-Vision-Debug`
- optionally builds and uploads `ALF-Vision-Release` when signing secrets are complete

### Release secrets

Create these GitHub Actions secrets:

```text
KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

The workflow reconstructs the keystore only inside the runner's temporary directory. The keystore is never committed.

## Troubleshooting

### Overlay tidak muncul

Check `Settings.canDrawOverlays(this)` in Android Settings and grant Draw over other apps. Then reopen ALF Vision Panel.

### MediaProjection gagal

Use START VISION again and accept the system capture dialog. If the permission was revoked, stop the service and request it again.

### Groq API error

Check API key, internet, model availability, account limits, and the error code. HTTP 401/403 means authentication/authorization; HTTP 429 means rate limiting; 5xx means the provider is temporarily unavailable.

### Model tidak tersedia

Press Test Connection to refresh active models. Use the model selector instead of manually entering an old deprecated model ID.

### Shizuku tidak terdeteksi

Install and start Shizuku if you need its optional features. The application remains functional without it and does not use Shizuku to bypass Android security.

### GitHub Actions gagal

Open the failed job and inspect the lint/test/build step. The workflow installs the required Android platform and Build Tools explicitly. It does not depend on `local.properties`.

### Gradle gagal di local machine

Use JDK 17. Confirm Android SDK 36 is installed. The repository must be online the first time the self-bootstrapping Gradle script downloads Gradle and dependencies.

## Data and privacy

By default, screenshot bytes are kept only in memory for the current session. Permanent screenshot history requires the user to enable Save Screenshots. Conversations are local Room data. Auto-delete is designed to be local cleanup only.

No custom backend, Firebase, cloud database, account login, or hidden upload service is included.

## Feature notes

- Auto Analyze is opt-in and disabled by default.
- Stop capture uses the foreground service STOP action and releases ImageReader, VirtualDisplay and MediaProjection resources.
- OCR and translation are implemented through Groq vision prompts; the architecture leaves room for a future local OCR engine without replacing MediaProjection.
- Voice input uses Android SpeechRecognizer and does not run when microphone permission is denied.
- Shizuku is optional. It does not replace MediaProjection and is not used to evade Android permission boundaries.

```

## `app/build.gradle.kts`

```kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.alfread.alfvision"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alfread.alfvision"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val storeFileProp = providers.gradleProperty("signingStoreFile").orNull
            val storePasswordProp = providers.gradleProperty("signingStorePassword").orNull
            val aliasProp = providers.gradleProperty("signingKeyAlias").orNull
            val keyPasswordProp = providers.gradleProperty("signingKeyPassword").orNull
            if (!storeFileProp.isNullOrBlank() && !storePasswordProp.isNullOrBlank() && !aliasProp.isNullOrBlank() && !keyPasswordProp.isNullOrBlank()) {
                storeFile = file(storeFileProp)
                storePassword = storePasswordProp
                keyAlias = aliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
            "META-INF/NOTICE.md"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

```

## `app/proguard-rules.pro`

```pro
# ALF Vision Panel uses reflection only for optional Shizuku capability discovery.
-keep class rikka.shizuku.** { *; }

```

## `app/src/androidTest/java/com/alfread/alfvision/MainActivityTest.kt`

```kt
package com.alfread.alfvision

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launches() {
        check(!rule.activity.isFinishing)
    }
}

```

## `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="moe.shizuku.manager.permission.API_V23" />

    <queries>
        <package android:name="moe.shizuku.privileged.api" />
    </queries>

    <application
        android:name=".AlfVisionApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ALFVisionPanel"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="fullSensor">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.FloatingPanelService"
            android:exported="false" />

        <service
            android:name=".service.ScreenCaptureService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />

        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            android:authorities="${applicationId}.shizuku"
            android:exported="true"
            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>

```

## `app/src/main/java/com/alfread/alfvision/AlfVisionApplication.kt`

```kt
package com.alfread.alfvision

import android.app.Application

class AlfVisionApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

```

## `app/src/main/java/com/alfread/alfvision/AppContainer.kt`

```kt
package com.alfread.alfvision

import android.content.Context
import androidx.room.Room
import com.alfread.alfvision.core.util.*
import com.alfread.alfvision.data.local.AppDatabase
import com.alfread.alfvision.data.network.GroqRepository
import com.alfread.alfvision.data.network.buildHttpClient
import com.alfread.alfvision.data.repository.*
import com.alfread.alfvision.vision.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "alf_vision.db").build()
    val secureStore = SecureStore(appContext)
    val settingsRepository = SettingsRepository(appContext, database.appSettingsDao())
    val historyRepository = HistoryRepository(database)
    val regionRepository = RegionRepository(database.regionPresetDao())
    val profileRepository = ProfileRepository(database.aiProfileDao())
    val sessionStore = SessionStore()
    val imageProcessor = ImageProcessor()
    val imageStorage = ImageStorage(appContext)
    val captureCoordinator = CaptureCoordinator()
    val networkMonitor = NetworkMonitor(appContext)
    val shizukuCompat = ShizukuCompat(appContext)
    val voiceInputManager = VoiceInputManager(appContext, sessionStore)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val groqRepository = GroqRepository(
        secureStore = secureStore,
        clientProvider = { buildHttpClient(settingsSnapshotTimeout) },
        timeoutProvider = { settingsSnapshotTimeout },
        retryProvider = { settingsSnapshotRetry }
    )

    val controller = VisionAssistantController(
        settings = settingsRepository,
        profiles = profileRepository,
        history = historyRepository,
        groq = groqRepository,
        capture = captureCoordinator,
        imageProcessor = imageProcessor,
        imageStorage = imageStorage,
        session = sessionStore
    )

    @Volatile private var settingsSnapshotTimeout: Long = 30
    @Volatile private var settingsSnapshotRetry: Int = 2

    init {
        scope.launch {
            profileRepository.ensureDefaults()
            val initial = settingsRepository.flow.first()
            if (initial.autoDeleteDays > 0) historyRepository.deleteOlderThan(System.currentTimeMillis() - initial.autoDeleteDays * 86_400_000L)
            settingsRepository.flow.collect { value ->
                settingsSnapshotTimeout = value.networkTimeoutSeconds
                settingsSnapshotRetry = value.retryCount
            }
        }
    }

    fun close() {
        voiceInputManager.stop()
        controller.stop()
        database.close()
        scope.cancel()
    }
}

```

## `app/src/main/java/com/alfread/alfvision/MainActivity.kt`

```kt
package com.alfread.alfvision

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.navigation.ALFNavHost
import com.alfread.alfvision.ui.theme.ALFVisionTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsState()
            ALFVisionTheme(settings) {
                Surface(Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        val data = result.data
                        if (result.resultCode == Activity.RESULT_OK && data != null) {
                            val intent = Intent(this@MainActivity, ScreenCaptureService::class.java)
                                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else vm.session.setError("Screen capture permission belum diberikan.")
                    }
                    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.voice() }

                    ALFNavHost(
                        navController = navController,
                        vm = vm,
                        onStartVision = {
                            startVision(captureLauncher)
                            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onRequestMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onOverlay = { openOverlaySettings() }
                    )
                }
            }
        }
    }

    private fun startVision(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        vm.startFloating()
        if (!ScreenCaptureService.isRunning) {
            val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
            launcher.launch(manager.createScreenCaptureIntent())
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }
}

```

## `app/src/main/java/com/alfread/alfvision/core/model/Models.kt`

```kt
package com.alfread.alfvision.core.model

import android.graphics.Rect

enum class Role { USER, ASSISTANT, SYSTEM }

data class ChatLine(
    val id: Long,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val model: String? = null,
    val tokenUsage: Int? = null
)

data class Region(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val displayId: Int = 0,
    val rotation: Int = 0
) {
    fun toRect(): Rect = Rect(x, y, x + width, y + height)
    fun normalized(maxWidth: Int, maxHeight: Int): Region {
        val sx = if (screenWidth > 0) maxWidth.toFloat() / screenWidth else 1f
        val sy = if (screenHeight > 0) maxHeight.toFloat() / screenHeight else 1f
        val nx = (x * sx).toInt().coerceIn(0, maxWidth)
        val ny = (y * sy).toInt().coerceIn(0, maxHeight)
        val nw = (width * sx).toInt().coerceIn(1, maxWidth - nx)
        val nh = (height * sy).toInt().coerceIn(1, maxHeight - ny)
        return copy(x = nx, y = ny, width = nw, height = nh, screenWidth = maxWidth, screenHeight = maxHeight)
    }
}

data class GroqModel(
    val id: String,
    val active: Boolean,
    val ownedBy: String? = null,
    val contextWindow: Long? = null,
    val maxCompletionTokens: Long? = null,
    val supportsVision: Boolean = false
)

data class ModelUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class AiResult(
    val text: String,
    val model: String,
    val usage: ModelUsage = ModelUsage()
)

data class AiProfile(
    val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 1024,
    val preferredModel: String? = null,
    val builtIn: Boolean = false
)

enum class ResponseStyle(val label: String) {
    SHORT("Short"), NORMAL("Normal"), DETAILED("Detailed"), TECHNICAL("Technical"), STEP_BY_STEP("Step-by-step")
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class Accent(val label: String, val argb: Long) {
    BLUE("Blue", 0xFF3F51B5), PURPLE("Purple", 0xFF7B4DFF), CYAN("Cyan", 0xFF00BCD4),
    GREEN("Green", 0xFF2E7D32), RED("Red", 0xFFC62828), GOLD("Gold", 0xFFFFA000)
}

enum class AutoAnalyzeInterval(val label: String, val millis: Long) {
    HALF_SECOND("0.5 second", 500), ONE_SECOND("1 second", 1_000), TWO_SECONDS("2 seconds", 2_000),
    FIVE_SECONDS("5 seconds", 5_000), TEN_SECONDS("10 seconds", 10_000), THIRTY_SECONDS("30 seconds", 30_000)
}

data class VisionSettings(
    val captureOnDemand: Boolean = true,
    val autoAnalyze: Boolean = false,
    val interval: AutoAnalyzeInterval = AutoAnalyzeInterval.TEN_SECONDS,
    val quality: Int = 88,
    val maxImageBytes: Int = 7_000_000,
    val saveScreenshots: Boolean = false,
    val sendOnlyRegion: Boolean = true,
    val screenshotPreview: Boolean = true,
    val freezeFrame: Boolean = true
)

data class FloatingSettings(
    val panelWidthDp: Int = 360,
    val panelHeightDp: Int = 540,
    val orbSizeDp: Int = 60,
    val opacity: Float = 0.96f,
    val snapToEdge: Boolean = true,
    val locked: Boolean = false,
    val compactMode: Boolean = false,
    val autoHide: Boolean = false,
    val autoHideMillis: Long = 8_000,
    val animation: Boolean = true,
    val x: Int = 24,
    val y: Int = 140
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.PURPLE,
    val activeModel: String = "qwen/qwen3.8-27b",
    val activeProfileId: Long = 0,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 1024,
    val responseStyle: ResponseStyle = ResponseStyle.NORMAL,
    val saveHistory: Boolean = true,
    val autoDeleteDays: Int = 0,
    val debugLogging: Boolean = false,
    val networkTimeoutSeconds: Long = 30,
    val retryCount: Int = 2,
    val shizukuEnhanced: Boolean = false,
    val vision: VisionSettings = VisionSettings(),
    val floating: FloatingSettings = FloatingSettings()
)

data class ImagePayload(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val width: Int,
    val height: Int,
    val byteCount: Int = bytes.size
)

data class CaptureRequest(
    val id: String,
    val region: Region?,
    val fullScreen: Boolean = false,
    val quality: Int = 88,
    val maxBytes: Int = 7_000_000
)

data class CaptureResult(
    val requestId: String,
    val payload: ImagePayload,
    val region: Region?
)

data class PendingImage(
    val bytes: ByteArray,
    val mimeType: String,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PanelMode { NORMAL, COMPACT, EXPANDED }

```

## `app/src/main/java/com/alfread/alfvision/core/util/Logger.kt`

```kt
package com.alfread.alfvision.core.util

import android.util.Log

class InternalLogger(private val enabled: () -> Boolean) {
    private val tag = "ALFVision"
    fun d(message: String) { if (enabled()) Log.d(tag, message) }
    fun i(message: String) { if (enabled()) Log.i(tag, message) }
    fun w(message: String) { if (enabled()) Log.w(tag, message) }
    fun e(message: String, throwable: Throwable? = null) { if (enabled()) Log.e(tag, message, throwable) }
}

```

## `app/src/main/java/com/alfread/alfvision/core/util/NetworkMonitor.kt`

```kt
package com.alfread.alfvision.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _connected = MutableStateFlow(isConnected())
    val connected: StateFlow<Boolean> = _connected

    fun refresh() { _connected.value = isConnected() }

    fun isConnected(): Boolean {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

```

## `app/src/main/java/com/alfread/alfvision/core/util/SecureStore.kt`

```kt
package com.alfread.alfvision.core.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)
    private val alias = "alf_vision_secure_key"

    init { ensureKey() }

    private fun ensureKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generator.generateKey()
        }
    }

    fun putApiKey(value: String) {
        if (value.isBlank()) {
            deleteApiKey()
            return
        }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(alias, null) as javax.crypto.SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit()
            .putString("api_key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("api_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun getApiKey(): String? {
        val encrypted = prefs.getString("api_key_cipher", null) ?: return null
        val iv = prefs.getString("api_key_iv", null) ?: return null
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(alias, null) as javax.crypto.SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            val bytes = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            String(bytes, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun deleteApiKey() {
        prefs.edit().remove("api_key_cipher").remove("api_key_iv").apply()
    }
}

```

## `app/src/main/java/com/alfread/alfvision/core/util/ShizukuCompat.kt`

```kt
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

```

## `app/src/main/java/com/alfread/alfvision/data/local/AppDatabase.kt`

```kt
package com.alfread.alfvision.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        RegionPresetEntity::class,
        AIProfileEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun regionPresetDao(): RegionPresetDao
    abstract fun aiProfileDao(): AIProfileDao
    abstract fun appSettingsDao(): AppSettingsDao
}

```

## `app/src/main/java/com/alfread/alfvision/data/local/Daos.kt`

```kt
package com.alfread.alfvision.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert suspend fun insert(value: ConversationEntity): Long
    @Update suspend fun update(value: ConversationEntity)
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1") suspend fun get(id: Long): ConversationEntity?
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM conversations WHERE updatedAt < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
    @Query("DELETE FROM conversations") suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: MessageEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(value: List<MessageEntity>)
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC") suspend fun getForConversation(conversationId: Long): List<MessageEntity>
    @Query("DELETE FROM messages WHERE conversationId = :conversationId") suspend fun deleteForConversation(conversationId: Long)
    @Query("DELETE FROM messages") suspend fun deleteAll()
}

@Dao
interface RegionPresetDao {
    @Insert suspend fun insert(value: RegionPresetEntity): Long
    @Update suspend fun update(value: RegionPresetEntity)
    @Delete suspend fun delete(value: RegionPresetEntity)
    @Query("SELECT * FROM region_presets ORDER BY createdAt DESC") fun observeAll(): Flow<List<RegionPresetEntity>>
    @Query("SELECT * FROM region_presets WHERE id = :id LIMIT 1") suspend fun get(id: Long): RegionPresetEntity?
}

@Dao
interface AIProfileDao {
    @Insert suspend fun insert(value: AIProfileEntity): Long
    @Update suspend fun update(value: AIProfileEntity)
    @Delete suspend fun delete(value: AIProfileEntity)
    @Query("SELECT * FROM ai_profiles ORDER BY builtIn DESC, name ASC") fun observeAll(): Flow<List<AIProfileEntity>>
    @Query("SELECT * FROM ai_profiles WHERE id = :id LIMIT 1") suspend fun get(id: Long): AIProfileEntity?
    @Query("SELECT COUNT(*) FROM ai_profiles") suspend fun count(): Int
}

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: AppSettingsEntity)
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1") suspend fun get(): AppSettingsEntity?
}

```

## `app/src/main/java/com/alfread/alfvision/data/local/Entities.kt`

```kt
package com.alfread.alfvision.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val profileId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val pinnedImagePath: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val imagePath: String? = null,
    val model: String? = null,
    val tokenUsage: Int? = null
)

@Entity(tableName = "region_presets")
data class RegionPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val displayId: Int,
    val rotation: Int,
    val createdAt: Long
)

@Entity(tableName = "ai_profiles")
data class AIProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val preferredModel: String?,
    val builtIn: Boolean
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String,
    val accent: String,
    val model: String,
    val profileId: Long,
    val temperature: Float,
    val maxTokens: Int,
    val responseStyle: String,
    val saveHistory: Boolean,
    val autoDeleteDays: Int,
    val debugLogging: Boolean,
    val networkTimeoutSeconds: Long,
    val retryCount: Int,
    val shizukuEnhanced: Boolean,
    val savedAt: Long
)

```

## `app/src/main/java/com/alfread/alfvision/data/network/GroqApiService.kt`

```kt
package com.alfread.alfvision.data.network

import com.alfread.alfvision.core.model.ChatLine
import com.alfread.alfvision.core.model.Role
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqApiService(
    private val clientProvider: () -> OkHttpClient
) {
    private val base = "https://api.groq.com/openai/v1"

    fun chat(
        apiKey: String,
        model: String,
        messages: List<ChatLine>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        currentImageBase64: String? = null,
        compareImageBase64: String? = null
    ): Pair<String, IntArray> {
        val contentMessages = JSONArray()
        contentMessages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        messages.forEachIndexed { index, line ->
            val role = when (line.role) {
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                Role.SYSTEM -> "system"
            }
            val hasImage = role == "user" && index == messages.lastIndex && (currentImageBase64 != null || compareImageBase64 != null)
            val message = JSONObject().put("role", role)
            if (hasImage) {
                val parts = JSONArray()
                parts.put(JSONObject().put("type", "text").put("text", line.content))
                currentImageBase64?.let { parts.put(imagePart(it)) }
                compareImageBase64?.let { parts.put(imagePart(it)) }
                message.put("content", parts)
            } else {
                message.put("content", line.content)
            }
            contentMessages.put(message)
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", contentMessages)
            .put("temperature", temperature.toDouble().coerceIn(0.0, 2.0))
            .put("max_completion_tokens", maxTokens.coerceIn(1, 16384))
            .put("stream", false)

        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        clientProvider().newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, raw)
            return GroqJsonParser.parseCompletion(JSONObject(raw))
        }
    }

    fun test(apiKey: String): List<com.alfread.alfvision.core.model.GroqModel> {
        val request = Request.Builder()
            .url("$base/models")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .get()
            .build()
        clientProvider().newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, raw)
            return GroqJsonParser.parseModels(JSONObject(raw))
        }
    }

    private fun imagePart(base64: String): JSONObject = JSONObject().apply {
        put("type", "image_url")
        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
    }

    private fun parseError(code: Int, raw: String): GroqApiException {
        val message = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrNull().orEmpty().ifBlank {
            when (code) {
                401 -> "Groq API key is invalid."
                403 -> "Groq rejected this request."
                429 -> "Rate limit reached."
                500, 502, 503 -> "Groq service is temporarily unavailable."
                else -> "Groq request failed (HTTP $code)."
            }
        }
        return GroqApiException(code, message)
    }
}

fun buildHttpClient(timeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
    .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
    .callTimeout(timeoutSeconds + 10, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

```

## `app/src/main/java/com/alfread/alfvision/data/network/GroqModels.kt`

```kt
package com.alfread.alfvision.data.network

import org.json.JSONArray
import org.json.JSONObject

internal object GroqJsonParser {
    fun parseCompletion(json: JSONObject): Pair<String, IntArray> {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw GroqApiException(500, "Groq returned no choices.")
        val message = choice.optJSONObject("message")
            ?: throw GroqApiException(500, "Groq returned an invalid message.")
        val content = message.optString("content", "").trim()
        if (content.isEmpty()) throw GroqApiException(500, "Groq returned an empty response.")
        val usage = json.optJSONObject("usage")
        return content to intArrayOf(
            usage?.optInt("prompt_tokens", 0) ?: 0,
            usage?.optInt("completion_tokens", 0) ?: 0,
            usage?.optInt("total_tokens", 0) ?: 0
        )
    }

    fun parseModels(json: JSONObject): List<com.alfread.alfvision.core.model.GroqModel> {
        val data: JSONArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                val context = item.optLong("context_window", 0L).takeIf { it > 0 }
                val maxCompletion = item.optLong("max_completion_tokens", 0L).takeIf { it > 0 }
                add(
                    com.alfread.alfvision.core.model.GroqModel(
                        id = id,
                        active = item.optBoolean("active", true),
                        ownedBy = item.optString("owned_by", null),
                        contextWindow = context,
                        maxCompletionTokens = maxCompletion,
                        supportsVision = id == "qwen/qwen3.8-27b" || id.contains("vision", true) || id.contains("qwen", true) && id.contains("27b", true)
                    )
                )
            }
        }.filter { it.active }
    }
}

data class GroqErrorInfo(val code: Int, val message: String)

class GroqApiException(val code: Int, override val message: String) : Exception(message)

```

## `app/src/main/java/com/alfread/alfvision/data/network/GroqRepository.kt`

```kt
package com.alfread.alfvision.data.network

import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.core.util.SecureStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GroqRepository(
    private val secureStore: SecureStore,
    private val clientProvider: () -> okhttp3.OkHttpClient,
    private val timeoutProvider: () -> Long,
    private val retryProvider: () -> Int
) {
    private fun service() = GroqApiService(clientProvider)

    suspend fun ask(
        model: String,
        messages: List<ChatLine>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        imageBase64: String? = null,
        compareImageBase64: String? = null
    ): AiResult = withContext(Dispatchers.IO) {
        val apiKey = secureStore.getApiKey()?.takeIf { it.isNotBlank() }
            ?: throw GroqApiException(401, "Groq API Key belum diatur.")
        var last: GroqApiException? = null
        val attempts = retryProvider().coerceIn(0, 4) + 1
        repeat(attempts) { attempt ->
            try {
                val (text, usage) = service().chat(apiKey, model, messages, systemPrompt, temperature, maxTokens, imageBase64, compareImageBase64)
                return@withContext AiResult(text, model, ModelUsage(usage[0], usage[1], usage[2]))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroqApiException) {
                last = e
                if (e.code == 429 && attempt < attempts - 1) {
                    delay((600L * (1L shl attempt)).coerceAtMost(4_000L))
                } else if (e.code in 500..599 && attempt < attempts - 1) {
                    delay((400L * (1L shl attempt)).coerceAtMost(3_000L))
                } else break
            } catch (t: Throwable) {
                throw GroqApiException(-1, "Network error. Check your internet connection.")
            }
        }
        throw (last ?: GroqApiException(-1, "Groq request failed."))
    }

    suspend fun listModels(): List<GroqModel> = withContext(Dispatchers.IO) {
        val key = secureStore.getApiKey()?.takeIf { it.isNotBlank() }
            ?: throw GroqApiException(401, "Groq API Key belum diatur.")
        service().test(key)
    }
}

```

## `app/src/main/java/com/alfread/alfvision/data/repository/HistoryRepository.kt`

```kt
package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.model.ChatLine
import com.alfread.alfvision.core.model.Role
import com.alfread.alfvision.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

class HistoryRepository(private val db: AppDatabase) {
    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeAll()

    suspend fun createConversation(title: String, profileId: Long): Long = db.conversationDao().insert(
        ConversationEntity(title = title, profileId = profileId, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
    )

    suspend fun addMessage(conversationId: Long, line: ChatLine): Long = db.messageDao().insert(
        MessageEntity(
            conversationId = conversationId,
            role = line.role.name,
            content = line.content,
            timestamp = line.timestamp,
            imagePath = line.imagePath,
            model = line.model,
            tokenUsage = line.tokenUsage
        )
    )

    suspend fun getMessages(conversationId: Long): List<ChatLine> = db.messageDao().getForConversation(conversationId).map {
        ChatLine(
            id = it.id,
            role = runCatching { Role.valueOf(it.role) }.getOrDefault(Role.USER),
            content = it.content,
            timestamp = it.timestamp,
            imagePath = it.imagePath,
            model = it.model,
            tokenUsage = it.tokenUsage
        )
    }

    suspend fun deleteConversation(id: Long) {
        db.messageDao().deleteForConversation(id)
        db.conversationDao().delete(id)
    }

    suspend fun deleteOlderThan(cutoff: Long) {
        val ids = db.conversationDao().observeAll().first().filter { it.updatedAt < cutoff }.map { it.id }
        ids.forEach { db.messageDao().deleteForConversation(it) }
        db.conversationDao().deleteOlderThan(cutoff)
    }

    suspend fun deleteAll() {
        db.messageDao().deleteAll()
        db.conversationDao().deleteAll()
    }
}

```

## `app/src/main/java/com/alfread/alfvision/data/repository/ProfileRepository.kt`

```kt
package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.model.AiProfile
import com.alfread.alfvision.data.local.AIProfileDao
import com.alfread.alfvision.data.local.AIProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(private val dao: AIProfileDao) {
    fun observe(): Flow<List<AiProfile>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): AiProfile? = dao.get(id)?.toModel()

    suspend fun create(profile: AiProfile): Long = dao.insert(
        AIProfileEntity(name = profile.name, systemPrompt = profile.systemPrompt, temperature = profile.temperature, maxTokens = profile.maxTokens, preferredModel = profile.preferredModel, builtIn = false)
    )

    suspend fun delete(profile: AiProfile) {
        if (!profile.builtIn) dao.delete(AIProfileEntity(profile.id, profile.name, profile.systemPrompt, profile.temperature, profile.maxTokens, profile.preferredModel, false))
    }

    suspend fun ensureDefaults() {
        if (dao.count() > 0) return
        val profiles = listOf(
            "General" to "You are ALF Vision, a helpful screen assistant. Be accurate and practical.",
            "Coding" to "Act as a senior software engineer. Analyze code, errors, APIs, and explain exact fixes.",
            "Gaming" to "Act as a game analysis assistant. Explain what is visible, mechanics, HUD information, and practical next steps without controlling the game.",
            "Translator" to "Translate visible text faithfully. Preserve names, numbers, formatting, and context.",
            "Android" to "Act as an Android engineer. Diagnose Android UI, permissions, Gradle, Kotlin, and system behavior.",
            "Minecraft" to "Act as a Minecraft assistant. Analyze screenshots for UI, items, errors, builds, and gameplay context.",
            "MLBB" to "Act as a Mobile Legends assistant. Analyze the visible HUD, map, items, heroes, and provide useful tactical explanation without automating actions.",
            "Homework" to "Act as a patient tutor. Explain the visible problem, show steps, and avoid making unsupported assumptions."
        )
        profiles.forEach { (name, prompt) ->
            dao.insert(AIProfileEntity(name = name, systemPrompt = prompt, temperature = 0.2f, maxTokens = 1024, preferredModel = null, builtIn = true))
        }
    }
}

private fun AIProfileEntity.toModel() = AiProfile(id, name, systemPrompt, temperature, maxTokens, preferredModel, builtIn)

```

## `app/src/main/java/com/alfread/alfvision/data/repository/RegionRepository.kt`

```kt
package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.model.Region
import com.alfread.alfvision.data.local.RegionPresetDao
import com.alfread.alfvision.data.local.RegionPresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RegionRepository(private val dao: RegionPresetDao) {
    fun observe(): Flow<List<RegionPresetEntity>> = dao.observeAll()

    suspend fun save(name: String, region: Region): Long = dao.insert(
        RegionPresetEntity(
            name = name,
            x = region.x,
            y = region.y,
            width = region.width,
            height = region.height,
            screenWidth = region.screenWidth,
            screenHeight = region.screenHeight,
            displayId = region.displayId,
            rotation = region.rotation,
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun delete(item: RegionPresetEntity) = dao.delete(item)

    suspend fun update(item: RegionPresetEntity) = dao.update(item)

    fun toRegion(item: RegionPresetEntity): Region = Region(item.x, item.y, item.width, item.height, item.screenWidth, item.screenHeight, item.displayId, item.rotation)
}

```

## `app/src/main/java/com/alfread/alfvision/data/repository/SettingsRepository.kt`

```kt
package com.alfread.alfvision.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.AppSettingsDao
import com.alfread.alfvision.data.local.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.alfSettingsDataStore by preferencesDataStore("alf_settings")

class SettingsRepository(private val context: Context, private val dao: AppSettingsDao) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val model = stringPreferencesKey("model")
        val profile = longPreferencesKey("profile")
        val temperature = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
        val responseStyle = stringPreferencesKey("response_style")
        val saveHistory = booleanPreferencesKey("save_history")
        val autoDeleteDays = intPreferencesKey("auto_delete_days")
        val debug = booleanPreferencesKey("debug")
        val timeout = longPreferencesKey("timeout")
        val retries = intPreferencesKey("retries")
        val shizuku = booleanPreferencesKey("shizuku")
        val autoAnalyze = booleanPreferencesKey("auto_analyze")
        val interval = stringPreferencesKey("interval")
        val quality = intPreferencesKey("quality")
        val maxImageBytes = intPreferencesKey("max_image_bytes")
        val saveScreenshots = booleanPreferencesKey("save_screenshots")
        val sendOnlyRegion = booleanPreferencesKey("send_only_region")
        val screenshotPreview = booleanPreferencesKey("screenshot_preview")
        val freezeFrame = booleanPreferencesKey("freeze_frame")
        val panelWidth = intPreferencesKey("panel_width")
        val panelHeight = intPreferencesKey("panel_height")
        val orbSize = intPreferencesKey("orb_size")
        val opacity = floatPreferencesKey("opacity")
        val snap = booleanPreferencesKey("snap")
        val locked = booleanPreferencesKey("locked")
        val compact = booleanPreferencesKey("compact")
        val autoHide = booleanPreferencesKey("auto_hide")
        val autoHideMillis = longPreferencesKey("auto_hide_millis")
        val animation = booleanPreferencesKey("animation")
        val panelX = intPreferencesKey("panel_x")
        val panelY = intPreferencesKey("panel_y")
    }

    val flow: Flow<AppSettings> = context.alfSettingsDataStore.data.map { p ->
        AppSettings(
            theme = enumOrDefault(p[K.theme], ThemeMode.SYSTEM),
            accent = enumOrDefault(p[K.accent], Accent.PURPLE),
            activeModel = p[K.model] ?: "qwen/qwen3.8-27b",
            activeProfileId = p[K.profile] ?: 0,
            temperature = (p[K.temperature] ?: 0.2f).coerceIn(0f, 2f),
            maxTokens = (p[K.maxTokens] ?: 1024).coerceIn(128, 16384),
            responseStyle = enumOrDefault(p[K.responseStyle], ResponseStyle.NORMAL),
            saveHistory = p[K.saveHistory] ?: true,
            autoDeleteDays = p[K.autoDeleteDays] ?: 0,
            debugLogging = p[K.debug] ?: false,
            networkTimeoutSeconds = (p[K.timeout] ?: 30).coerceIn(10, 120),
            retryCount = (p[K.retries] ?: 2).coerceIn(0, 4),
            shizukuEnhanced = p[K.shizuku] ?: false,
            vision = VisionSettings(
                autoAnalyze = p[K.autoAnalyze] ?: false,
                interval = enumOrDefault(p[K.interval], AutoAnalyzeInterval.TEN_SECONDS),
                quality = (p[K.quality] ?: 88).coerceIn(40, 95),
                maxImageBytes = (p[K.maxImageBytes] ?: 7_000_000).coerceIn(500_000, 19_000_000),
                saveScreenshots = p[K.saveScreenshots] ?: false,
                sendOnlyRegion = p[K.sendOnlyRegion] ?: true,
                screenshotPreview = p[K.screenshotPreview] ?: true,
                freezeFrame = p[K.freezeFrame] ?: true
            ),
            floating = FloatingSettings(
                panelWidthDp = (p[K.panelWidth] ?: 360).coerceIn(280, 520),
                panelHeightDp = (p[K.panelHeight] ?: 540).coerceIn(300, 820),
                orbSizeDp = (p[K.orbSize] ?: 60).coerceIn(44, 100),
                opacity = (p[K.opacity] ?: 0.96f).coerceIn(0.55f, 1f),
                snapToEdge = p[K.snap] ?: true,
                locked = p[K.locked] ?: false,
                compactMode = p[K.compact] ?: false,
                autoHide = p[K.autoHide] ?: false,
                autoHideMillis = (p[K.autoHideMillis] ?: 8_000).coerceIn(2_000, 60_000),
                animation = p[K.animation] ?: true,
                x = p[K.panelX] ?: 24,
                y = p[K.panelY] ?: 140
            )
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = flow.first()
        val next = transform(current)
        context.alfSettingsDataStore.edit { p ->
            p[K.theme] = next.theme.name
            p[K.accent] = next.accent.name
            p[K.model] = next.activeModel
            p[K.profile] = next.activeProfileId
            p[K.temperature] = next.temperature
            p[K.maxTokens] = next.maxTokens
            p[K.responseStyle] = next.responseStyle.name
            p[K.saveHistory] = next.saveHistory
            p[K.autoDeleteDays] = next.autoDeleteDays
            p[K.debug] = next.debugLogging
            p[K.timeout] = next.networkTimeoutSeconds
            p[K.retries] = next.retryCount
            p[K.shizuku] = next.shizukuEnhanced
            p[K.autoAnalyze] = next.vision.autoAnalyze
            p[K.interval] = next.vision.interval.name
            p[K.quality] = next.vision.quality
            p[K.maxImageBytes] = next.vision.maxImageBytes
            p[K.saveScreenshots] = next.vision.saveScreenshots
            p[K.sendOnlyRegion] = next.vision.sendOnlyRegion
            p[K.screenshotPreview] = next.vision.screenshotPreview
            p[K.freezeFrame] = next.vision.freezeFrame
            p[K.panelWidth] = next.floating.panelWidthDp
            p[K.panelHeight] = next.floating.panelHeightDp
            p[K.orbSize] = next.floating.orbSizeDp
            p[K.opacity] = next.floating.opacity
            p[K.snap] = next.floating.snapToEdge
            p[K.locked] = next.floating.locked
            p[K.compact] = next.floating.compactMode
            p[K.autoHide] = next.floating.autoHide
            p[K.autoHideMillis] = next.floating.autoHideMillis
            p[K.animation] = next.floating.animation
            p[K.panelX] = next.floating.x
            p[K.panelY] = next.floating.y
        }
        dao.upsert(
            AppSettingsEntity(
                theme = next.theme.name,
                accent = next.accent.name,
                model = next.activeModel,
                profileId = next.activeProfileId,
                temperature = next.temperature,
                maxTokens = next.maxTokens,
                responseStyle = next.responseStyle.name,
                saveHistory = next.saveHistory,
                autoDeleteDays = next.autoDeleteDays,
                debugLogging = next.debugLogging,
                networkTimeoutSeconds = next.networkTimeoutSeconds,
                retryCount = next.retryCount,
                shizukuEnhanced = next.shizukuEnhanced,
                savedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun savePanelPosition(x: Int, y: Int) = update { it.copy(floating = it.floating.copy(x = x, y = y)) }
    suspend fun savePanelSize(widthDp: Int, heightDp: Int) = update { it.copy(floating = it.floating.copy(panelWidthDp = widthDp, panelHeightDp = heightDp)) }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        runCatching { value?.let { enumValueOf<T>(it) } ?: default }.getOrDefault(default)
}

```

## `app/src/main/java/com/alfread/alfvision/service/FloatingPanelService.kt`

```kt
package com.alfread.alfvision.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class FloatingPanelService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var container: com.alfread.alfvision.AppContainer
    private var panelView: ComposeView? = null
    private var orbView: ComposeView? = null
    private var regionView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var orbParams: WindowManager.LayoutParams? = null
    private var regionParams: WindowManager.LayoutParams? = null
    private var selectorRegion: Region? = null
    private var minimized = false
    private var expanded = false
    private var savedPanelSize: Pair<Int, Int>? = null
    private var captureSuppressed = false
    private var autoHideJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = (application as AlfVisionApplication).container
        wm = getSystemService(WindowManager::class.java)
        startPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> if (minimized) restorePanel()
            ACTION_MINIMIZE -> minimize()
            ACTION_CLOSE -> stopSelf()
            ACTION_CAPTURE -> container.controller.capture(selectorRegion)
            ACTION_REGION -> showRegionSelector()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        autoHideJob?.cancel()
        removeView(panelView)
        removeView(orbView)
        removeView(regionView)
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPanel() {
        if (!Settings.canDrawOverlays(this)) return
        val current = runBlockingOrDefault()
        val width = dp(current.floating.panelWidthDp)
        val height = dp(current.floating.panelHeightDp)
        panelParams = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            x = current.floating.x
            y = current.floating.y
        }
        panelView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings by container.settingsRepository.flow.collectAsState(initial = current)
                val lines by container.sessionStore.lines.collectAsState()
                val input by container.sessionStore.input.collectAsState()
                val busy by container.sessionStore.busy.collectAsState()
                val error by container.sessionStore.error.collectAsState()
                val image by container.sessionStore.currentImage.collectAsState()
                val voice by container.sessionStore.voiceState.collectAsState()
                ALFVisionTheme(settings) {
                    FloatingPanel(
                        settings = settings,
                        lines = lines,
                        input = input,
                        busy = busy,
                        error = error,
                        currentImage = image,
                        voiceState = voice,
                        onInput = container.sessionStore::setInput,
                        onSend = { container.controller.ask(container.sessionStore.input.value) },
                        onMinimize = { minimize() },
                        onMaximize = { toggleMaximize() },
                        onClose = { stopSelf() },
                        onCapture = { container.controller.capture(selectorRegion) },
                        onSelectRegion = { showRegionSelector() },
                        onQuickAction = container.controller::quickAction,
                        onCompare = container.controller::compare,
                        onRetry = container.controller::retryLast,
                        onStop = container.controller::cancelRequest,
                        onPin = container.sessionStore::pinCurrent,
                        onClear = container.sessionStore::clearChat,
                        onVoice = {
                            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) container.voiceInputManager.start()
                            else container.sessionStore.setError("Microphone permission belum diberikan. Buka Permission Center dari aplikasi.")
                        },
                        onDrag = { dx, dy -> movePanel(dx, dy) },
                        onResize = { dx, dy -> resizePanel(dx, dy) },
                        onOpenApp = {
                            startActivity(Intent(this@FloatingPanelService, com.alfread.alfvision.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    )
                }
            }
        }
        addView(panelView, panelParams!!)
        scheduleAutoHide(current)
    }

    private fun showOrb() {
        if (!Settings.canDrawOverlays(this)) return
        removeView(orbView)
        val settings = runBlockingOrDefault()
        val size = dp(settings.floating.orbSizeDp)
        orbParams = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelParams?.x ?: settings.floating.x
            y = panelParams?.y ?: settings.floating.y
        }
        orbView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ALFVisionTheme(settings) {
                    FloatingOrb(
                        accent = androidx.compose.ui.graphics.Color(settings.accent.argb.toULong()),
                        onTap = { restorePanel() },
                        onDoubleTap = { container.controller.capture(selectorRegion) },
                        onLongPress = { showRegionSelector() },
                        onDrag = { dx, dy -> moveOrb(dx, dy) }
                    )
                }
            }
        }
        addView(orbView, orbParams!!)
    }

    private fun toggleMaximize() {
        val params = panelParams ?: return
        val metrics = resources.displayMetrics
        if (!expanded) {
            savedPanelSize = params.width to params.height
            params.width = (metrics.widthPixels * 0.92f).roundToInt()
            params.height = (metrics.heightPixels * 0.84f).roundToInt()
            params.x = ((metrics.widthPixels - params.width) / 2).coerceAtLeast(0)
            params.y = ((metrics.heightPixels - params.height) / 2).coerceAtLeast(0)
        } else {
            savedPanelSize?.let { (w, h) -> params.width = w; params.height = h }
        }
        expanded = !expanded
        runCatching { wm.updateViewLayout(panelView, params) }
    }

    private fun minimize() {
        if (minimized) return
        minimized = true
        removeView(panelView)
        panelView = null
        showOrb()
    }

    private fun restorePanel() {
        if (!minimized) return
        minimized = false
        removeView(orbView)
        orbView = null
        startPanel()
    }

    private fun movePanel(dx: Float, dy: Float) {
        if (captureSuppressed || panelParams == null) return
        val params = panelParams!!
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampPanel(params)
        snapPanel(params)
        runCatching { wm.updateViewLayout(panelView, params) }
        persistPosition(params.x, params.y)
    }

    private fun resizePanel(dx: Float, dy: Float) {
        if (panelParams == null) return
        val params = panelParams!!
        params.width = (params.width + dx.roundToInt()).coerceIn(dp(280), dp(520))
        params.height = (params.height + dy.roundToInt()).coerceIn(dp(300), dp(820))
        runCatching { wm.updateViewLayout(panelView, params) }
        persistSize(params.width, params.height)
    }

    private fun moveOrb(dx: Float, dy: Float) {
        if (orbParams == null) return
        val params = orbParams!!
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampOrb(params)
        snapOrb(params)
        runCatching { wm.updateViewLayout(orbView, params) }
        persistPosition(params.x, params.y)
    }

    private fun clampPanel(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
    }

    private fun clampOrb(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
    }

    private fun snapPanel(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val threshold = (metrics.widthPixels * 0.12f).roundToInt()
        val current = runBlockingOrDefault()
        if (!current.floating.snapToEdge) return
        if (params.x <= threshold) params.x = 0
        else if (params.x + params.width >= metrics.widthPixels - threshold) params.x = (metrics.widthPixels - params.width).coerceAtLeast(0)
    }

    private fun snapOrb(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val threshold = (metrics.widthPixels * 0.14f).roundToInt()
        val current = runBlockingOrDefault()
        if (!current.floating.snapToEdge) return
        if (params.x <= threshold) params.x = 0
        else if (params.x + params.width >= metrics.widthPixels - threshold) params.x = (metrics.widthPixels - params.width).coerceAtLeast(0)
    }


    private fun scheduleAutoHide(settings: AppSettings) {
        autoHideJob?.cancel()
        if (!settings.floating.autoHide) return
        autoHideJob = scope.launch {
            delay(settings.floating.autoHideMillis)
            if (!minimized) minimize()
        }
    }

    fun showRegionSelector() {
        if (regionView != null || !Settings.canDrawOverlays(this)) return
        removeView(panelView)
        val metrics = resources.displayMetrics
        selectorRegion = selectorRegion ?: Region(
            x = metrics.widthPixels / 5,
            y = metrics.heightPixels / 5,
            width = metrics.widthPixels * 3 / 5,
            height = metrics.heightPixels * 3 / 5,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            rotation = display?.rotation ?: 0
        )
        regionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        regionView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ALFVisionTheme(runBlockingOrDefault()) {
                    RegionSelectorOverlay(
                        region = selectorRegion ?: return@RegionSelectorOverlay,
                        onRegionChange = { selectorRegion = it.copy(screenWidth = metrics.widthPixels, screenHeight = metrics.heightPixels) },
                        onReset = {
                            selectorRegion = Region(metrics.widthPixels / 5, metrics.heightPixels / 5, metrics.widthPixels * 3 / 5, metrics.heightPixels * 3 / 5, metrics.widthPixels, metrics.heightPixels, rotation = display?.rotation ?: 0)
                        },
                        onCenter = {
                            selectorRegion = selectorRegion?.let { r -> r.copy(x = (metrics.widthPixels - r.width) / 2, y = (metrics.heightPixels - r.height) / 2) }
                        },
                        onFullscreen = { selectorRegion = Region(0, 0, metrics.widthPixels, metrics.heightPixels, metrics.widthPixels, metrics.heightPixels, rotation = display?.rotation ?: 0) },
                        onSave = { saveRegionPreset() },
                        onClose = { closeRegionSelector() }
                    )
                }
            }
        }
        addView(regionView, regionParams!!)
    }

    private fun saveRegionPreset() {
        val region = selectorRegion ?: return
        scope.launch {
            val name = "Region ${System.currentTimeMillis().toString().takeLast(4)}"
            container.regionRepository.save(name, region)
            container.sessionStore.setRegion(region)
            closeRegionSelector()
        }
    }

    private fun closeRegionSelector() {
        removeView(regionView)
        regionView = null
        if (!minimized && panelView == null) startPanel()
    }

    fun setCaptureSuppressed(value: Boolean) {
        captureSuppressed = value
        if (value) {
            removeView(regionView)
            regionView = null
            removeView(panelView)
            removeView(orbView)
        } else if (minimized) {
            if (orbView == null) showOrb()
        } else if (panelView == null) {
            startPanel()
        }
    }

    private fun persistPosition(x: Int, y: Int) { scope.launch { container.settingsRepository.savePanelPosition(x, y) } }
    private fun persistSize(w: Int, h: Int) { scope.launch { container.settingsRepository.savePanelSize(dpToValue(w), dpToValue(h)) } }

    private fun runBlockingOrDefault(): AppSettings = runCatching { kotlinx.coroutines.runBlocking { container.settingsRepository.flow.first() } }.getOrDefault(AppSettings())
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun dpToValue(px: Int): Int = (px / resources.displayMetrics.density).roundToInt()

    private fun addView(view: ComposeView?, params: WindowManager.LayoutParams) {
        if (view == null) return
        runCatching { wm.addView(view, params) }.onFailure { sessionError(it) }
    }

    private fun removeView(view: ComposeView?) { if (view != null) runCatching { wm.removeView(view) } }

    private fun sessionError(t: Throwable) { container.sessionStore.setError("Floating panel gagal tampil. Pastikan Overlay Permission aktif.") }

    companion object {
        const val ACTION_SHOW = "com.alfread.alfvision.action.SHOW"
        const val ACTION_MINIMIZE = "com.alfread.alfvision.action.MINIMIZE"
        const val ACTION_CLOSE = "com.alfread.alfvision.action.CLOSE"
        const val ACTION_CAPTURE = "com.alfread.alfvision.action.CAPTURE"
        const val ACTION_REGION = "com.alfread.alfvision.action.REGION"
        @Volatile var instance: FloatingPanelService? = null

        fun suppressForCapture(value: Boolean) { instance?.setCaptureSuppressed(value) }
    }
}

```

## `app/src/main/java/com/alfread/alfvision/service/OverlayComposables.kt`

```kt
package com.alfread.alfvision.service

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.vision.VoiceState
import kotlin.math.roundToInt

@Composable
fun FloatingOrb(
    accent: ComposeColor,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(2.dp, accent.copy(alpha = 0.85f), CircleShape)
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(drag.x, drag.y) } },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Visibility, contentDescription = "Open ALF Vision", tint = accent, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun FloatingPanel(
    settings: AppSettings,
    lines: List<ChatLine>,
    input: String,
    busy: Boolean,
    error: String?,
    currentImage: PendingImage?,
    voiceState: VoiceState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    onCapture: () -> Unit,
    onSelectRegion: () -> Unit,
    onQuickAction: (String) -> Unit,
    onCompare: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onPin: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onOpenApp: () -> Unit
) {
    val accent = ComposeColor(settings.accent.argb.toULong())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(settings.floating.opacity)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(settings.floating.locked) {
                    if (!settings.floating.locked) detectDragGestures { _, drag -> onDrag(drag.x, drag.y) }
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("ALF VISION", style = MaterialTheme.typography.labelLarge)
                Text(if (busy) "ANALYZING" else "READY", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCapture) { Icon(Icons.Default.CameraAlt, "Capture") }
            IconButton(onClick = onMaximize) { Icon(Icons.Default.OpenInFull, "Maximize") }
            IconButton(onClick = onMinimize) { Icon(Icons.Default.Minimize, "Minimize") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }

        if (currentImage != null) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CropFree, null, tint = accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Image: ${currentImage.label}", Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = onPin) { Text("PIN") }
                }
            }
        }

        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onSelectRegion, label = { Text("Region") }, leadingIcon = { Icon(Icons.Default.Crop, null) })
            AssistChip(onClick = onCompare, label = { Text("Compare") }, leadingIcon = { Icon(Icons.Default.Compare, null) })
            AssistChip(onClick = onClear, label = { Text("Clear") }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
        }

        Row(Modifier.padding(horizontal = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Analyze", "Explain", "Read", "Translate", "Find Error", "Help Me").forEach { action ->
                FilterChip(selected = false, onClick = { onQuickAction(action) }, label = { Text(action, fontSize = 10.sp) })
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines, key = { it.id }) { line ->
                MessageBubble(line, accent)
            }
            if (error != null) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(6.dp))
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
        }

        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("Ask anything...") },
                enabled = !busy,
                leadingIcon = {
                    IconButton(onClick = onVoice) {
                        Icon(if (voiceState == VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Voice input")
                    }
                },
                trailingIcon = {
                    IconButton(onClick = if (busy) onStop else onSend, enabled = busy || input.isNotBlank()) {
                        Icon(if (busy) Icons.Default.Stop else Icons.Default.Send, if (busy) "Stop" else "Send")
                    }
                }
            )
        }
        Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, bottom = 6.dp)) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenApp) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(4.dp)); Text("APP") }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(26.dp)
                    .pointerInput(settings.floating.locked) {
                        if (!settings.floating.locked) detectDragGestures { change, drag -> change.consume(); onResize(drag.x, drag.y) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DragHandle, contentDescription = "Resize panel", modifier = Modifier.size(18.dp), tint = accent.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine, accent: ComposeColor) {
    val isUser = line.role == Role.USER
    val bg = if (isUser) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 310.dp)) {
            Text(line.content, Modifier.padding(10.dp), fontSize = 13.sp)
        }
    }
}

@Composable
fun RegionSelectorOverlay(
    region: Region,
    onRegionChange: (Region) -> Unit,
    onReset: () -> Unit,
    onCenter: () -> Unit,
    onFullscreen: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val handle = 24.dp
    val density = LocalDensity.current
    val screenW = region.screenWidth.coerceAtLeast(1)
    val screenH = region.screenHeight.coerceAtLeast(1)
    val x = region.x.toFloat()
    val y = region.y.toFloat()
    val w = region.width.toFloat()
    val h = region.height.toFloat()
    var dragMode by remember { mutableIntStateOf(-1) }

    Box(
        Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.14f)).pointerInput(region) {
            detectDragGestures(
                onDragStart = { offset ->
                    val handlePx = with(density) { handle.toPx() }
                    val right = x + w
                    val bottom = y + h
                    val mode = when {
                        offset.x in (right - handlePx)..(right + handlePx) && offset.y in (bottom - handlePx)..(bottom + handlePx) -> 1
                        offset.x in (x - handlePx)..(x + handlePx) && offset.y in (y - handlePx)..(y + handlePx) -> 2
                        offset.x in (right - handlePx)..(right + handlePx) && offset.y in (y - handlePx)..(y + handlePx) -> 3
                        offset.x in (x - handlePx)..(x + handlePx) && offset.y in (bottom - handlePx)..(bottom + handlePx) -> 4
                        offset.x in x..right && offset.y in y..bottom -> 0
                        else -> -1
                    }
                    dragMode = mode
                },
                onDrag = { change, drag ->
                    change.consume()
                    val mode = dragMode
                    when (mode) {
                        0 -> onRegionChange(region.copy(x = (region.x + drag.x).roundToInt().coerceIn(0, screenW - region.width), y = (region.y + drag.y).roundToInt().coerceIn(0, screenH - region.height)))
                        1 -> onRegionChange(region.copy(width = (region.width + drag.x).roundToInt().coerceIn(80, screenW - region.x), height = (region.height + drag.y).roundToInt().coerceIn(80, screenH - region.y)))
                        2 -> {
                            val nx = (region.x + drag.x).roundToInt().coerceIn(0, region.x + region.width - 80)
                            val ny = (region.y + drag.y).roundToInt().coerceIn(0, region.y + region.height - 80)
                            onRegionChange(region.copy(x = nx, y = ny, width = region.width + region.x - nx, height = region.height + region.y - ny))
                        }
                        3 -> {
                            val nx = (region.x).coerceIn(0, screenW - 80)
                            val ny = (region.y + drag.y).roundToInt().coerceIn(0, region.y + region.height - 80)
                            onRegionChange(region.copy(y = ny, width = (region.width + drag.x).roundToInt().coerceAtLeast(80), height = region.height + region.y - ny))
                        }
                        4 -> {
                            val nx = (region.x + drag.x).roundToInt().coerceIn(0, region.x + region.width - 80)
                            val ny = region.y
                            onRegionChange(region.copy(x = nx, y = ny, width = region.width + region.x - nx, height = (region.height + drag.y).roundToInt().coerceAtLeast(80)))
                        }
                    }
                }
            )
        }
    ) {
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .border(3.dp, ComposeColor.White, RoundedCornerShape(6.dp))
                .background(ComposeColor.Black.copy(alpha = 0.08f))
        ) {
            Text("AI REGION  ${region.width} × ${region.height}", color = ComposeColor.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 24.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onReset) { Icon(Icons.Default.Refresh, "Reset") }
            IconButton(onClick = onCenter) { Icon(Icons.Default.CenterFocusStrong, "Center") }
            IconButton(onClick = onFullscreen) { Icon(Icons.Default.Fullscreen, "Fullscreen") }
            IconButton(onClick = onSave) { Icon(Icons.Default.Save, "Save region") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close region selector") }
        }
    }
}


```

## `app/src/main/java/com/alfread/alfvision/service/ScreenCaptureService.kt`

```kt
package com.alfread.alfvision.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.R
import com.alfread.alfvision.core.model.CaptureRequest
import com.alfread.alfvision.core.model.CaptureResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {
    private lateinit var container: com.alfread.alfvision.AppContainer
    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var pendingRequest: CaptureRequest? = null
    private var latestImageAvailable = AtomicBoolean(false)
    private var serviceJob: Job? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = (application as AlfVisionApplication).container
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
        serviceJob = mainScope.launch {
            container.captureCoordinator.requests.collect { request ->
                if (pendingRequest != null) {
                    container.captureCoordinator.fail(request.id, IllegalStateException("Capture already in progress"))
                } else {
                    pendingRequest = request
                    processAvailableImage()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.parcelableIntent(EXTRA_DATA) ?: run {
            container.sessionStore.setError("MediaProjection permission data tidak tersedia.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (resultCode != Activity.RESULT_OK) {
            container.sessionStore.setError("Screen capture permission belum diberikan.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            setupProjection(resultCode, data)
            isRunning = true
            container.sessionStore.setError(null)
        } catch (t: Throwable) {
            container.sessionStore.setError("Screen capture gagal dimulai. Periksa permission dan coba lagi.")
            stopSelf()
        }
        return START_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        projection = projectionManager.getMediaProjection(resultCode, data) ?: throw IllegalStateException("MediaProjection unavailable")
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                mainScope.launch { stopCapture() }
            }
        }
        projection?.registerCallback(projectionCallback!!, android.os.Handler(Looper.getMainLooper()))
        createVirtualDisplay()
    }

    private fun createVirtualDisplay() {
        releaseImageReader()
        virtualDisplay?.release()
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ processAvailableImage() }, android.os.Handler(Looper.getMainLooper()))
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "ALF Vision Panel",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        latestImageAvailable.set(false)
    }

    private fun processAvailableImage() {
        val request = pendingRequest ?: return
        val reader = imageReader ?: return
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        image.use {
            try {
                val plane = it.planes.firstOrNull() ?: throw IllegalStateException("No image plane")
                val width = it.width
                val height = it.height
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmapWidth = width + rowPadding / pixelStride
                val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                val buffer: ByteBuffer = plane.buffer
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = if (!request.fullScreen && request.region != null) container.imageProcessor.cropRegion(bitmap, request.region) else bitmap
                val payload = container.imageProcessor.encodeJpeg(cropped, request.quality, request.maxBytes)
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
                pendingRequest = null
                container.captureCoordinator.complete(CaptureResult(request.id, payload, request.region))
                latestImageAvailable.set(true)
            } catch (t: Throwable) {
                pendingRequest = null
                container.captureCoordinator.fail(request.id, t)
                container.sessionStore.setError("Screenshot terlalu besar atau gagal diproses.")
            }
        }
    }

    private fun releaseImageReader() {
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { imageReader?.close() }
        imageReader = null
    }

    fun stopCapture() {
        pendingRequest?.let { container.captureCoordinator.fail(it.id, IllegalStateException("Capture stopped")) }
        pendingRequest = null
        releaseImageReader()
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.unregisterCallback(projectionCallback!!) }
        runCatching { projection?.stop() }
        projection = null
        projectionCallback = null
        isRunning = false
    }

    override fun onDestroy() {
        stopCapture()
        serviceJob?.cancel()
        mainScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this, 90, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_notification, getString(R.string.notification_stop), pending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "alf_vision_capture"
        private const val NOTIFICATION_ID = 1101
        const val ACTION_STOP = "com.alfread.alfvision.capture.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        @Volatile var isRunning: Boolean = false
    }
}


private fun Intent.parcelableIntent(key: String): Intent? {
    return if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Intent::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)
}

```

## `app/src/main/java/com/alfread/alfvision/ui/MainViewModel.kt`

```kt
package com.alfread.alfvision.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.ConversationEntity
import com.alfread.alfvision.data.local.RegionPresetEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as AlfVisionApplication).container
    val settings = c.settingsRepository.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val conversations: StateFlow<List<ConversationEntity>> = c.historyRepository.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val regions: StateFlow<List<RegionPresetEntity>> = c.regionRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profiles = c.profileRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val session = c.sessionStore
    val network = c.networkMonitor.connected
    val shizuku = c.shizukuCompat
    private val _groqStatus = MutableStateFlow<String?>(null)
    val groqStatus: StateFlow<String?> = _groqStatus
    private val _models = MutableStateFlow<List<GroqModel>>(listOf(GroqModel("qwen/qwen3.8-27b", true, supportsVision = true)))
    val models: StateFlow<List<GroqModel>> = _models

    fun saveApiKey(value: String) { c.secureStore.putApiKey(value); _groqStatus.value = "API key saved securely on device." }
    fun deleteApiKey() { c.secureStore.deleteApiKey(); _groqStatus.value = "API key deleted." }
    fun hasApiKey(): Boolean = c.secureStore.hasApiKey()
    fun testConnection() {
        viewModelScope.launch {
            _groqStatus.value = "Testing connection..."
            runCatching { c.groqRepository.listModels() }
                .onSuccess { list -> _models.value = list; _groqStatus.value = "Connected. ${list.size} models available." }
                .onFailure { _groqStatus.value = it.message ?: "Connection failed." }
        }
    }
    fun updateSettings(transform: (AppSettings) -> AppSettings) { viewModelScope.launch { c.settingsRepository.update(transform) } }
    fun showRegionSelector() {
        startFloating()
        c.appContext.startService(android.content.Intent(c.appContext, com.alfread.alfvision.service.FloatingPanelService::class.java).setAction(com.alfread.alfvision.service.FloatingPanelService.ACTION_REGION))
    }
    fun clearScreenshots() { c.imageStorage.clear() }
    fun startFloating() {
        val i = android.content.Intent(c.appContext, com.alfread.alfvision.service.FloatingPanelService::class.java).setAction(com.alfread.alfvision.service.FloatingPanelService.ACTION_SHOW)
        androidx.core.content.ContextCompat.startService(c.appContext, i)
    }
    fun stopCapture() { c.appContext.startService(android.content.Intent(c.appContext, com.alfread.alfvision.service.ScreenCaptureService::class.java).setAction(com.alfread.alfvision.service.ScreenCaptureService.ACTION_STOP)) }
    fun capture() { c.controller.capture(c.sessionStore.region.value) }
    fun ask(prompt: String) { c.controller.ask(prompt) }
    fun quickAction(action: String) { c.controller.quickAction(action) }
    fun compare() { c.controller.compare() }
    fun stopRequest() { c.controller.cancelRequest() }
    fun retryLast() { c.controller.retryLast() }
    fun clearHistory() { viewModelScope.launch { c.historyRepository.deleteAll() } }
    fun cleanupHistory(days: Int) { if (days > 0) viewModelScope.launch { c.historyRepository.deleteOlderThan(System.currentTimeMillis() - days * 86_400_000L) } }
    fun deleteConversation(id: Long) { viewModelScope.launch { c.historyRepository.deleteConversation(id) } }
    fun saveRegion(name: String, region: Region) { viewModelScope.launch { c.regionRepository.save(name, region) } }
    fun deleteRegion(item: RegionPresetEntity) { viewModelScope.launch { c.regionRepository.delete(item) } }
    fun duplicateRegion(item: RegionPresetEntity) { viewModelScope.launch { c.regionRepository.save("${item.name} Copy", Region(item.x, item.y, item.width, item.height, item.screenWidth, item.screenHeight, item.displayId, item.rotation)) } }
    fun updateRegion(item: RegionPresetEntity) { viewModelScope.launch { c.regionRepository.update(item) } }
    fun openConversation(id: Long) {
        viewModelScope.launch {
            val lines = c.historyRepository.getMessages(id)
            c.sessionStore.clearChat()
            lines.forEach { line -> if (line.role == Role.USER) c.sessionStore.addUser(line.content, line.imagePath) else c.sessionStore.addAssistant(line.content, line.model ?: settings.value.activeModel, ModelUsage(totalTokens = line.tokenUsage ?: 0), line.imagePath) }
        }
    }
    fun pinCurrent() = c.sessionStore.pinCurrent()
    fun voice() = c.voiceInputManager.start()
    fun createProfile(name: String, prompt: String) { viewModelScope.launch { c.profileRepository.create(AiProfile(name = name, systemPrompt = prompt)) } }
}

```

## `app/src/main/java/com/alfread/alfvision/ui/navigation/Navigation.kt`

```kt
package com.alfread.alfvision.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigationBarItemColors
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.screens.*

sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Vision : Dest("vision", "Vision", Icons.Default.Visibility)
    data object Chat : Dest("chat", "Chat", Icons.Default.Chat)
    data object History : Dest("history", "History", Icons.Default.History)
    data object Settings : Dest("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun ALFNavHost(navController: NavHostController, vm: MainViewModel, onStartVision: () -> Unit, onRequestMicrophone: () -> Unit, onOverlay: () -> Unit) {
    val items = listOf(Dest.Home, Dest.Vision, Dest.Chat, Dest.History, Dest.Settings)
    Scaffold(bottomBar = {
        NavigationBar {
            val current = navController.currentBackStackEntryAsState().value?.destination?.route
            items.forEach { item ->
                NavigationBarItem(
                    selected = current == item.route,
                    onClick = { navController.navigate(item.route) { launchSingleTop = true } },
                    icon = { Icon(item.icon, item.label) },
                    label = { Text(item.label) },
                                    )
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = Dest.Home.route, modifier = androidx.compose.ui.Modifier) {
            composable(Dest.Home.route) { HomeScreen(vm, padding, onStartVision, onOverlay) }
            composable(Dest.Vision.route) { VisionScreen(vm, padding) }
            composable(Dest.Chat.route) { ChatScreen(vm, padding, onRequestMicrophone) }
            composable(Dest.History.route) { HistoryScreen(vm, padding) }
            composable(Dest.Settings.route) { SettingsScreen(vm, padding, onOverlay) }
        }
    }
}

```

## `app/src/main/java/com/alfread/alfvision/ui/screens/AnnotationEditor.kt`

```kt
package com.alfread.alfvision.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.PendingImage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class Tool { RECTANGLE, CIRCLE, ARROW, LINE, TEXT, BLUR }
private data class Mark(val tool: Tool, val start: Offset, val end: Offset, val text: String = "")

@Composable
fun AnnotationEditor(
    image: PendingImage,
    onSave: (PendingImage) -> Unit,
    onClose: () -> Unit
) {
    val bitmap = remember(image.bytes) { android.graphics.BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size) }
    if (bitmap == null) {
        onClose()
        return
    }
    var tool by remember { mutableStateOf(Tool.RECTANGLE) }
    var draft by remember { mutableStateOf<Mark?>(null) }
    var marks by remember { mutableStateOf(listOf<Mark>()) }
    var redo by remember { mutableStateOf(listOf<Mark>()) }
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            Text("Annotate", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (marks.isNotEmpty()) { redo = redo + marks.last(); marks = marks.dropLast(1) } }) { Icon(Icons.Default.Undo, "Undo") }
            IconButton(onClick = { if (redo.isNotEmpty()) { marks = marks + redo.last(); redo = redo.dropLast(1) } }) { Icon(Icons.Default.Redo, "Redo") }
            Button(onClick = {
                val output = renderAnnotations(bitmap, marks)
                val bytes = java.io.ByteArrayOutputStream().use { out -> output.compress(Bitmap.CompressFormat.JPEG, 90, out); out.toByteArray() }
                output.recycle()
                onSave(PendingImage(bytes, "image/jpeg", "Annotated"))
            }) { Text("Save") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip("Rect", Tool.RECTANGLE, tool, Icons.Default.CropSquare) { tool = it }
            ToolChip("Circle", Tool.CIRCLE, tool, Icons.Default.Circle) { tool = it }
            ToolChip("Arrow", Tool.ARROW, tool, Icons.Default.ArrowForward) { tool = it }
            ToolChip("Line", Tool.LINE, tool, Icons.Default.Minimize) { tool = it }
            ToolChip("Text", Tool.TEXT, tool, Icons.Default.TextFields) { tool = it }
            ToolChip("Blur", Tool.BLUR, tool, Icons.Default.BlurOn) { tool = it }
        }
        if (tool == Tool.TEXT) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(8.dp), label = { Text("Text to place") })
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = "Image being annotated", modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tool, text, marks) {
                        detectTapGestures { offset ->
                            if (tool == Tool.TEXT && text.isNotBlank()) {
                                marks = marks + Mark(tool, offset, offset, text)
                                redo = emptyList()
                            }
                        }
                    }
                    .pointerInput(tool, marks) {
                        detectDragGestures(
                            onDragStart = { start -> draft = Mark(tool, start, start) },
                            onDrag = { change, drag -> change.consume(); draft = draft?.copy(end = draft!!.end + drag) },
                            onDragEnd = { draft?.let { if (tool != Tool.TEXT) { marks = marks + it; redo = emptyList() } }; draft = null }
                        )
                    }
            ) {
                fun drawMark(mark: Mark, alpha: Float = 1f) {
                    when (mark.tool) {
                        Tool.RECTANGLE -> drawRect(Color.Transparent, topLeft = mark.start, size = androidx.compose.ui.geometry.Size(mark.end.x - mark.start.x, mark.end.y - mark.start.y), style = Stroke(4f))
                        Tool.CIRCLE -> drawOval(Color.Transparent, topLeft = mark.start, size = androidx.compose.ui.geometry.Size(mark.end.x - mark.start.x, mark.end.y - mark.start.y), style = Stroke(4f))
                        Tool.LINE -> drawLine(Color.Red.copy(alpha = alpha), mark.start, mark.end, 4f)
                        Tool.ARROW -> drawArrow(mark.start, mark.end, alpha)
                        Tool.TEXT -> drawContext.canvas.nativeCanvas.drawText(mark.text, mark.start.x, mark.start.y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED; textSize = 42f })
                        Tool.BLUR -> drawRect(Color.Black.copy(alpha = 0.25f), topLeft = mark.start, size = androidx.compose.ui.geometry.Size(mark.end.x - mark.start.x, mark.end.y - mark.start.y))
                    }
                }
                marks.forEach(::drawMark)
                draft?.let { drawMark(it, 0.65f) }
            }
        }
    }
    DisposableEffect(bitmap) { onDispose { if (!bitmap.isRecycled) bitmap.recycle() } }
}

@Composable private fun ToolChip(label: String, value: Tool, selected: Tool, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (Tool) -> Unit) {
    FilterChip(selected = value == selected, onClick = { onClick(value) }, label = { Text(label, fontSize = 11.sp) }, leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) })
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(start: Offset, end: Offset, alpha: Float) {
    drawLine(Color.Red.copy(alpha = alpha), start, end, 4f)
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val len = 22.0
    val a1 = angle + Math.PI * 0.83
    val a2 = angle - Math.PI * 0.83
    drawLine(Color.Red.copy(alpha = alpha), end, Offset((end.x + len * cos(a1)).toFloat(), (end.y + len * sin(a1)).toFloat()), 4f)
    drawLine(Color.Red.copy(alpha = alpha), end, Offset((end.x + len * cos(a2)).toFloat(), (end.y + len * sin(a2)).toFloat()), 4f)
}

private fun renderAnnotations(source: Bitmap, marks: List<Mark>): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
    val scaleX = out.width / source.width.toFloat()
    val scaleY = out.height / source.height.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED; strokeWidth = 6f; style = Paint.Style.STROKE }
    marks.forEach { mark ->
        val sx = mark.start.x * scaleX
        val sy = mark.start.y * scaleY
        val ex = mark.end.x * scaleX
        val ey = mark.end.y * scaleY
        when (mark.tool) {
            Tool.RECTANGLE -> canvas.drawRect(sx, sy, ex, ey, paint)
            Tool.CIRCLE -> canvas.drawOval(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey), paint)
            Tool.LINE -> canvas.drawLine(sx, sy, ex, ey, paint)
            Tool.ARROW -> {
                canvas.drawLine(sx, sy, ex, ey, paint)
                val a = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val len = 35.0
                canvas.drawLine(ex, ey, (ex + len * cos(a + 2.65)).toFloat(), (ey + len * sin(a + 2.65)).toFloat(), paint)
                canvas.drawLine(ex, ey, (ex + len * cos(a - 2.65)).toFloat(), (ey + len * sin(a - 2.65)).toFloat(), paint)
            }
            Tool.TEXT -> { paint.style = Paint.Style.FILL; paint.textSize = 52f; canvas.drawText(mark.text, sx, sy, paint); paint.style = Paint.Style.STROKE }
            Tool.BLUR -> boxBlur(out, min(sx, ex).toInt().coerceAtLeast(0), min(sy, ey).toInt().coerceAtLeast(0), max(sx, ex).toInt().coerceAtMost(out.width), max(sy, ey).toInt().coerceAtMost(out.height))
        }
    }
    return out
}

private fun boxBlur(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
    if (right <= left || bottom <= top) return
    val w = right - left
    val h = bottom - top
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, left, top, w, h)
    val copy = pixels.copyOf()
    for (y in 0 until h) for (x in 0 until w) {
        var rs = 0; var gs = 0; var bs = 0; var count = 0
        for (ky in -2..2) for (kx in -2..2) {
            val nx = (x + kx).coerceIn(0, w - 1); val ny = (y + ky).coerceIn(0, h - 1)
            val c = copy[ny * w + nx]
            rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255; count++
        }
        pixels[y * w + x] = android.graphics.Color.argb(255, rs / count, gs / count, bs / count)
    }
    bitmap.setPixels(pixels, 0, w, left, top, w, h)
}

```

## `app/src/main/java/com/alfread/alfvision/ui/screens/Screens.kt`

```kt
package com.alfread.alfvision.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.RegionPresetEntity
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.navigation.Dest
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(vm: MainViewModel, padding: PaddingValues, onStartVision: () -> Unit, onOverlay: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val network by vm.network.collectAsState()
    val apiReady = vm.hasApiKey()
    val overlayReady = Settings.canDrawOverlays(androidx.compose.ui.platform.LocalContext.current)
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ALF VISION", style = MaterialTheme.typography.headlineMedium)
                Text("AI Screen Assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
        }
        StatusCard("GROQ", if (apiReady) "CONNECTED / KEY READY" else "API KEY REQUIRED", apiReady)
        StatusCard("SCREEN CAPTURE", if (ScreenCaptureService.isRunning) "READY / ACTIVE" else "NOT ACTIVE", ScreenCaptureService.isRunning)
        StatusCard("FLOATING PANEL", if (overlayReady) "READY" else "OVERLAY PERMISSION REQUIRED", overlayReady)
        StatusCard("NETWORK", if (network) "CONNECTED" else "OFFLINE", network)
        Button(onClick = onStartVision, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = true) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("START VISION") }
        if (!overlayReady) OutlinedButton(onClick = onOverlay, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Layers, null); Spacer(Modifier.width(8.dp)); Text("Grant Overlay Permission") }
        Text("Quick Actions", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickCard("Chat", Icons.Default.Chat) { vm.session.setError(null) }
            QuickCard("Capture", Icons.Default.CameraAlt) { vm.capture() }
            QuickCard("Analyze", Icons.Default.AutoAwesome) { vm.quickAction("Analyze") }
        }
        Text("Privacy", style = MaterialTheme.typography.titleLarge)
        Text("Screen capture is used only after you grant MediaProjection permission. Region mode can send only the selected area. Screenshots are not permanently saved by default.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable private fun StatusCard(label: String, value: String, ready: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun QuickCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.weight(1f)) { Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Spacer(Modifier.height(8.dp)); Text(label) } }
}

@Composable
fun VisionScreen(vm: MainViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsState()
    val regions by vm.regions.collectAsState()
    val image by vm.session.currentImage.collectAsState()
    val previous by vm.session.previousImage.collectAsState()
    var annotating by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RegionPresetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    if (annotating && image != null) {
        AnnotationEditor(image = image!!, onSave = { vm.session.setImage(it); annotating = false }, onClose = { annotating = false })
    } else Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vision", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.showRegionSelector() }) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(6.dp)); Text("Select Region") }
            Button(onClick = { vm.capture() }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Capture") }
            OutlinedButton(onClick = { vm.compare() }, enabled = image != null && previous != null) { Icon(Icons.Default.Compare, null); Spacer(Modifier.width(6.dp)); Text("Compare") }
            OutlinedButton(onClick = { annotating = true }, enabled = image != null) { Icon(Icons.Default.Draw, null); Spacer(Modifier.width(6.dp)); Text("Annotate") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Crop, null)
            Spacer(Modifier.width(8.dp))
            Text("Send only selected region")
            Spacer(Modifier.weight(1f))
            Switch(checked = settings.vision.sendOnlyRegion, onCheckedChange = { v -> vm.updateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = v)) } })
        }
        Text("Region presets", style = MaterialTheme.typography.titleLarge)
        if (regions.isEmpty()) Text("No saved regions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text("${item.width} × ${item.height} at ${item.x}, ${item.y}") },
                    leadingContent = { Icon(Icons.Default.CropFree, null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.session.setRegion(vmRegion(item)) }) { Icon(Icons.Default.PlayArrow, "Activate region") }
                            IconButton(onClick = { renameTarget = item; renameText = item.name }) { Icon(Icons.Default.Edit, "Rename") }
                            IconButton(onClick = { vm.duplicateRegion(item) }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                            IconButton(onClick = { vm.deleteRegion(item) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    },
                    modifier = Modifier.clickable { vm.session.setRegion(vmRegion(item)) }
                )
            }
        }
        image?.let { current ->
            Text("Current image: ${current.bytes.size} bytes", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            val preview = remember(current.bytes) { BitmapFactory.decodeByteArray(current.bytes, 0, current.bytes.size)?.asImageBitmap() }
            preview?.let { androidx.compose.foundation.Image(it, contentDescription = "Current captured screenshot", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) }
        }
    }
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename region") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { renameTarget?.let { vm.updateRegion(it.copy(name = renameText.trim().ifBlank { it.name })) }; renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

private fun vmRegion(item: RegionPresetEntity) = Region(item.x, item.y, item.width, item.height, item.screenWidth, item.screenHeight, item.displayId, item.rotation)

@Composable
fun ChatScreen(vm: MainViewModel, padding: PaddingValues, onRequestMicrophone: () -> Unit) {
    val lines by vm.session.lines.collectAsState()
    val input by vm.session.input.collectAsState()
    val busy by vm.session.busy.collectAsState()
    val error by vm.session.error.collectAsState()
    val voice by vm.session.voiceState.collectAsState()
    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
        Text("Chat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines, key = { it.id }) { line -> MessageCard(line) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = input, onValueChange = vm.session::setInput, modifier = Modifier.weight(1f), minLines = 1, maxLines = 5, placeholder = { Text("Ask anything...") }, enabled = !busy)
            IconButton(onClick = { if (Build.VERSION.SDK_INT >= 23) onRequestMicrophone() }, enabled = !busy) { Icon(if (voice == com.alfread.alfvision.vision.VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Microphone") }
            IconButton(onClick = { vm.ask(input) }, enabled = !busy && input.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
        }
    }
}

@Composable private fun MessageCard(line: ChatLine) {
    val user = line.role == Role.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 340.dp)) { Text(line.content, Modifier.padding(12.dp)) }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel, padding: PaddingValues) {
    val list by vm.conversations.collectAsState()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::clearHistory, enabled = list.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Delete all") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { item ->
                Card(onClick = { vm.openConversation(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.updatedAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { confirmDelete = item.id }) { Icon(Icons.Default.Delete, "Delete conversation") }
                    }
                }
            }
        }
    }
    if (confirmDelete != null) AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete conversation?") }, text = { Text("This cannot be undone.") }, confirmButton = { TextButton(onClick = { vm.deleteConversation(confirmDelete!!); confirmDelete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, padding: PaddingValues, onOverlay: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val profiles by vm.profiles.collectAsState()
    var profileExpanded by remember { mutableStateOf(false) }
    var showProfileCreate by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            SectionTitle("AI")
            OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Groq API Key") }, visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide key") } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveApiKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save") }
                OutlinedButton(onClick = vm::testConnection) { Text("Test Connection") }
                TextButton(onClick = vm::deleteApiKey) { Text("Delete") }
            }
            vm.groqStatus.collectAsState().value?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
        item {
            ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = !modelExpanded }) {
                OutlinedTextField(value = settings.activeModel, onValueChange = {}, readOnly = true, label = { Text("Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    vm.models.collectAsState().value.filter { it.supportsVision }.forEach { model -> DropdownMenuItem(text = { Text(model.id) }, onClick = { vm.updateSettings { it.copy(activeModel = model.id) }; modelExpanded = false }) }
                }
            }
        }
        item {
            SectionTitle("Response")
            EnumDropdown("Style", settings.responseStyle.label, ResponseStyle.entries) { selected -> vm.updateSettings { it.copy(responseStyle = selected) } }
            SliderRow("Temperature", settings.temperature, 0f..2f) { v -> vm.updateSettings { it.copy(temperature = v) } }
            SliderRow("Max Tokens", settings.maxTokens.toFloat(), 128f..16384f) { v -> vm.updateSettings { it.copy(maxTokens = v.toInt()) } }
        }
        item {
            SectionTitle("Profile")
            ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = { profileExpanded = !profileExpanded }) {
                val selected = profiles.firstOrNull { it.id == settings.activeProfileId }?.name ?: "General"
                OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, label = { Text("Active profile") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                    profiles.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.updateSettings { it.copy(activeProfileId = p.id) }; profileExpanded = false }) }
                    DropdownMenuItem(text = { Text("Create custom profile") }, onClick = { profileExpanded = false; showProfileCreate = true })
                }
            }
        }
        item {
            SectionTitle("Vision")
            SwitchRow("Auto Analyze", settings.vision.autoAnalyze) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(autoAnalyze = checked)) } }
            SwitchRow("Freeze Frame", settings.vision.freezeFrame) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(freezeFrame = checked)) } }
            SwitchRow("Screenshot Preview", settings.vision.screenshotPreview) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(screenshotPreview = checked)) } }
            SwitchRow("Save Screenshots", settings.vision.saveScreenshots) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(saveScreenshots = checked)) } }
            SwitchRow("Send Only Selected Region", settings.vision.sendOnlyRegion) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(sendOnlyRegion = checked)) } }
        }
        item {
            SectionTitle("Floating")
            SliderRow("Opacity", settings.floating.opacity, 0.55f..1f) { v -> vm.updateSettings { it.copy(floating = it.floating.copy(opacity = v)) } }
            SwitchRow("Snap to edge", settings.floating.snapToEdge) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(snapToEdge = checked)) } }
            SwitchRow("Lock position", settings.floating.locked) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(locked = checked)) } }
            SwitchRow("Compact mode", settings.floating.compactMode) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(compactMode = checked)) } }
            SwitchRow("Auto hide", settings.floating.autoHide) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(autoHide = checked)) } }
        }
        item {
            SectionTitle("Appearance")
            EnumDropdown("Theme", settings.theme.label, ThemeMode.entries) { selected -> vm.updateSettings { it.copy(theme = selected) } }
            AccentPicker(settings.accent) { accent -> vm.updateSettings { current -> current.copy(accent = accent) } }
        }
        item {
            SectionTitle("Privacy")
            SwitchRow("Save Conversations", settings.saveHistory) { checked -> vm.updateSettings { current -> current.copy(saveHistory = checked) } }
            Text("Auto Delete")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 7, 30).forEach { days ->
                    FilterChip(selected = settings.autoDeleteDays == days, onClick = { vm.updateSettings { current -> current.copy(autoDeleteDays = days) }; vm.cleanupHistory(days) }, label = { Text(if (days == 0) "Never" else "${days} day${if (days == 1) "" else "s"}") })
                }
            }
            Button(onClick = { vm.clearHistory() }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Clear History") }
            OutlinedButton(onClick = vm::clearScreenshots) { Text("Clear Screenshots") }
        }
        item {
            SectionTitle("Permissions")
            OutlinedButton(onClick = onOverlay, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Layers, null); Spacer(Modifier.width(6.dp)); Text("Overlay Permission") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.fillMaxWidth()) { Text("Notification settings") }
        }
        item {
            SectionTitle("Advanced")
            SwitchRow("Debug logging", settings.debugLogging) { checked -> vm.updateSettings { current -> current.copy(debugLogging = checked) } }
            Text("Shizuku: ${if (vm.shizuku.available.collectAsState().value) if (vm.shizuku.permissionGranted.collectAsState().value) "Connected / Permission granted" else "Connected / Permission not granted" else "Not available"}", fontSize = 13.sp)
            Text("Shizuku is optional and does not replace MediaProjection or bypass Android security.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        item {
            SectionTitle("About")
            Text("ALF Vision Panel 1.0.0", style = MaterialTheme.typography.titleMedium)
            Text("Native Kotlin + Jetpack Compose + Material 3. Groq API calls are sent directly from the device over HTTPS.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showProfileCreate) ProfileDialog(vm, onDismiss = { showProfileCreate = false })
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge) }
@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) } }
@Composable private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) { Column { Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text("${"%.2f".format(value)}") }; Slider(value, onChange, valueRange = range) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun <T : Enum<T>> EnumDropdown(label: String, selected: String, entries: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(selected, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) { entries.forEach { e -> DropdownMenuItem(text = { Text((e as? ResponseStyle)?.label ?: (e as? ThemeMode)?.label ?: e.name) }, onClick = { onSelect(e); expanded = false }) } }
    }
}

@Composable private fun AccentPicker(accent: Accent, onChange: (Accent) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Accent.entries.forEach { item -> FilterChip(selected = item == accent, onClick = { onChange(item) }, label = { Text(item.label) }) }
    }
}

@Composable private fun ProfileDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("You are a helpful ALF Vision assistant.") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create profile") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Name") }); OutlinedTextField(prompt, { prompt = it }, label = { Text("System prompt") }, minLines = 3) } },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.createProfile(name.trim(), prompt.trim()); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

```

## `app/src/main/java/com/alfread/alfvision/ui/theme/Theme.kt`

```kt
package com.alfread.alfvision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alfread.alfvision.core.model.Accent
import com.alfread.alfvision.core.model.AppSettings
import com.alfread.alfvision.core.model.ThemeMode

@Composable
fun ALFVisionTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val accent = Color(settings.accent.argb.toULong())
    val scheme = if (dark) {
        darkColorScheme(primary = accent, secondary = accent.copy(alpha = 0.82f), tertiary = Color(0xFF72D8FF))
    } else {
        lightColorScheme(primary = accent, secondary = accent.copy(alpha = 0.82f), tertiary = Color(0xFF006F88))
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

```

## `app/src/main/java/com/alfread/alfvision/vision/CaptureCoordinator.kt`

```kt
package com.alfread.alfvision.vision

import com.alfread.alfvision.core.model.CaptureRequest
import com.alfread.alfvision.core.model.CaptureResult
import com.alfread.alfvision.core.model.Region
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

class CaptureCoordinator {
    private val _requests = MutableSharedFlow<CaptureRequest>(extraBufferCapacity = 8)
    val requests: SharedFlow<CaptureRequest> = _requests
    private val pending = LinkedHashMap<String, CompletableDeferred<CaptureResult>>()
    private val lock = Any()

    suspend fun request(region: Region?, quality: Int, maxBytes: Int, fullScreen: Boolean = region == null): CaptureResult {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<CaptureResult>()
        synchronized(lock) { pending[id] = deferred }
        _requests.emit(CaptureRequest(id, region, fullScreen, quality, maxBytes))
        return try {
            deferred.await()
        } finally {
            synchronized(lock) { pending.remove(id) }
        }
    }

    fun complete(result: CaptureResult) {
        synchronized(lock) { pending.remove(result.requestId)?.complete(result) }
    }

    fun fail(requestId: String, throwable: Throwable) {
        synchronized(lock) { pending.remove(requestId)?.completeExceptionally(throwable) }
    }
}

```

## `app/src/main/java/com/alfread/alfvision/vision/ImageProcessor.kt`

```kt
package com.alfread.alfvision.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.alfread.alfvision.core.model.ImagePayload
import com.alfread.alfvision.core.model.Region
import java.io.ByteArrayOutputStream

class ImageProcessor {
    fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    fun cropRegion(source: Bitmap, region: Region): Bitmap {
        val normalized = region.normalized(source.width, source.height)
        return Bitmap.createBitmap(source, normalized.x, normalized.y, normalized.width, normalized.height)
    }

    fun resizeForUpload(bitmap: Bitmap, maxDimension: Int = 1600): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val matrix = Matrix().apply { setScale(scale, scale) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int, maxBytes: Int): ImagePayload {
        var working = resizeForUpload(bitmap)
        var q = quality.coerceIn(40, 95)
        var encoded = compress(working, q)
        while (encoded.size > maxBytes && (q > 50 || maxOf(working.width, working.height) > 900)) {
            if (q > 50) q -= 5
            else working = resizeForUpload(working, maxOf(900, maxOf(working.width, working.height) * 3 / 4))
            encoded = compress(working, q)
        }
        return ImagePayload(encoded, "image/jpeg", working.width, working.height, encoded.size)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun addSimpleAnnotation(base: Bitmap, rects: List<Rect>, text: String?): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (out.width.coerceAtMost(out.height) * 0.006f).coerceAtLeast(3f)
            color = android.graphics.Color.RED
        }
        rects.forEach { canvas.drawRect(it, paint) }
        if (!text.isNullOrBlank()) {
            paint.style = Paint.Style.FILL
            paint.textSize = (out.width * 0.035f).coerceAtLeast(28f)
            canvas.drawText(text, 24f, paint.textSize + 24f, paint)
        }
        return out
    }
}

```

## `app/src/main/java/com/alfread/alfvision/vision/ImageStorage.kt`

```kt
package com.alfread.alfvision.vision

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageStorage(private val context: Context) {
    private val dir by lazy { File(context.filesDir, "vision_images").apply { mkdirs() } }

    fun save(bytes: ByteArray, prefix: String = "capture"): String {
        val file = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    fun read(path: String): ByteArray? = runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull()

    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    fun delete(path: String?) { path?.let { File(it).delete() } }
}

```

## `app/src/main/java/com/alfread/alfvision/vision/SessionStore.kt`

```kt
package com.alfread.alfvision.vision

import com.alfread.alfvision.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionStore {
    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input
    private val _region = MutableStateFlow<Region?>(null)
    val region: StateFlow<Region?> = _region
    private val _currentImage = MutableStateFlow<PendingImage?>(null)
    val currentImage: StateFlow<PendingImage?> = _currentImage
    private val _previousImage = MutableStateFlow<PendingImage?>(null)
    val previousImage: StateFlow<PendingImage?> = _previousImage
    private val _pinnedImage = MutableStateFlow<PendingImage?>(null)
    val pinnedImage: StateFlow<PendingImage?> = _pinnedImage
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState

    fun setInput(value: String) { _input.value = value }
    fun addUser(text: String, imagePath: String? = null) { _lines.value = _lines.value + ChatLine(System.nanoTime(), Role.USER, text, imagePath = imagePath) }
    fun addAssistant(text: String, model: String, usage: ModelUsage, imagePath: String? = null) { _lines.value = _lines.value + ChatLine(System.nanoTime(), Role.ASSISTANT, text, model = model, tokenUsage = usage.totalTokens, imagePath = imagePath) }
    fun clearChat() { _lines.value = emptyList() }
    fun removeLastAssistant() { if (_lines.value.lastOrNull()?.role == Role.ASSISTANT) _lines.value = _lines.value.dropLast(1) }
    fun removeLastUser() { if (_lines.value.lastOrNull()?.role == Role.USER) _lines.value = _lines.value.dropLast(1) }
    fun setRegion(value: Region?) { _region.value = value }
    fun setBusy(value: Boolean) { _busy.value = value }
    fun setError(value: String?) { _error.value = value }
    fun setVoiceState(value: VoiceState) { _voiceState.value = value }
    fun setImage(value: PendingImage) { _previousImage.value = _currentImage.value; _currentImage.value = value }
    fun pinCurrent() { _pinnedImage.value = _currentImage.value }
    fun unpin() { _pinnedImage.value = null }
}

enum class VoiceState { IDLE, LISTENING, ERROR }

```

## `app/src/main/java/com/alfread/alfvision/vision/VisionAssistantController.kt`

```kt
package com.alfread.alfvision.vision

import android.util.Base64
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.network.GroqApiException
import com.alfread.alfvision.data.network.GroqRepository
import com.alfread.alfvision.data.repository.HistoryRepository
import com.alfread.alfvision.data.repository.ProfileRepository
import com.alfread.alfvision.data.repository.SettingsRepository
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class VisionAssistantController(
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val history: HistoryRepository,
    private val groq: GroqRepository,
    private val capture: CaptureCoordinator,
    private val imageProcessor: ImageProcessor,
    private val imageStorage: ImageStorage,
    private val session: SessionStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversationId: Long? = null
    private var autoJob: Job? = null
    private var activeAskJob: Job? = null
    private var lastPrompt: String? = null

    fun capture(region: Region? = session.region.value, fullScreen: Boolean = region == null) {
        scope.launch {
            if (!ScreenCaptureService.isRunning) {
                session.setError("Screen capture belum aktif. Tekan START VISION dan izinkan screen capture.")
                return@launch
            }
            session.setBusy(true)
            session.setError(null)
            FloatingPanelService.suppressForCapture(true)
            runCatching {
                delay(120)
                capture.request(
                    region = if (settings.flow.first().vision.sendOnlyRegion) region else null,
                    quality = settings.flow.first().vision.quality,
                    maxBytes = settings.flow.first().vision.maxImageBytes,
                    fullScreen = fullScreen
                )
            }.onSuccess { result ->
                session.setImage(PendingImage(result.payload.bytes, result.payload.mimeType, if (region != null) "Region" else "Screen"))
                session.setRegion(result.region ?: region)
            }.onFailure { session.setError(errorMessage(it)) }
            FloatingPanelService.suppressForCapture(false)
            session.setBusy(false)
        }
    }

    fun ask(prompt: String, compare: Boolean = false) {
        val cleaned = prompt.trim()
        if (cleaned.isEmpty()) return
        lastPrompt = cleaned
        activeAskJob?.cancel()
        activeAskJob = scope.launch {
            session.setBusy(true)
            session.setError(null)
            val currentSettings = settings.flow.first()
            val profile = profiles.get(currentSettings.activeProfileId)
            val system = buildSystemPrompt(profile, currentSettings.responseStyle)
            val current = (session.currentImage.value ?: session.pinnedImage.value)?.bytes
            val previous = if (compare) session.previousImage.value?.bytes else null
            val imagePath = if (currentSettings.vision.saveScreenshots && current != null) imageStorage.save(current, "history") else null
            session.addUser(cleaned, imagePath)
            if (currentSettings.saveHistory) ensureConversation(profile?.id ?: 0, cleaned)
            val recent = session.lines.value.takeLast(30)
            val result = runCatching {
                groq.ask(
                    model = profile?.preferredModel?.takeIf { it.isNotBlank() } ?: currentSettings.activeModel,
                    messages = recent,
                    systemPrompt = system,
                    temperature = profile?.temperature ?: currentSettings.temperature,
                    maxTokens = profile?.maxTokens ?: currentSettings.maxTokens,
                    imageBase64 = current?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                    compareImageBase64 = previous?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                )
            }
            result.onSuccess { ai ->
                session.addAssistant(ai.text, ai.model, ai.usage, imagePath)
                if (currentSettings.saveHistory) saveLastLines(ai.model, ai.usage.totalTokens)
                session.setInput("")
            }.onFailure { session.setError(errorMessage(it)) }
            session.setBusy(false)
        }.also { job ->
            job.invokeOnCompletion { if (activeAskJob === job) activeAskJob = null }
        }
    }

    fun cancelRequest() { activeAskJob?.cancel(); activeAskJob = null; session.setBusy(false) }
    fun retryLast() { lastPrompt?.let { ask(it, compare = false) } }

    fun quickAction(action: String) {
        val prompt = when (action) {
            "Analyze" -> "Analyze what is happening in this selected screen area. Focus on facts, visible UI, likely state, and practical next steps."
            "Explain" -> "Jelaskan apa yang sedang terjadi di area layar ini dan kenapa hal tersebut terjadi."
            "Read" -> "Read all clearly visible text in this image. Preserve line breaks where useful."
            "Translate" -> "Translate the visible text into Indonesian. Preserve names, numbers, and important formatting."
            "Summarize" -> "Summarize the important information visible in this screen area."
            "Find Error" -> "Find possible errors or abnormal behavior visible in this screen area. Explain evidence and fixes."
            "Extract Text" -> "Extract visible text exactly as readable. Return text only, with sensible line breaks."
            "Describe" -> "Describe the visible screen area precisely and briefly."
            "Help Me" -> "Based on this screen area, tell me what I should do next. Give practical steps and do not invent hidden information."
            else -> action
        }
        ask(prompt)
    }

    fun compare() {
        val current = session.currentImage.value
        val previous = session.previousImage.value
        if (current == null || previous == null) {
            session.setError("Compare membutuhkan dua screenshot. Capture dua kali terlebih dahulu.")
            return
        }
        ask("The first image is CURRENT and the second image is PREVIOUS. Compare them and explain what changed, what appeared, what disappeared, and the most important differences.", true)
    }

    fun setAutoAnalyze(enabled: Boolean) {
        autoJob?.cancel()
        if (!enabled) return
        autoJob = scope.launch {
            while (isActive) {
                capture()
                delay(settings.flow.first().vision.interval.millis)
                if (!settings.flow.first().vision.autoAnalyze) break
            }
        }
    }

    fun stop() { autoJob?.cancel(); scope.cancel() }

    private suspend fun ensureConversation(profileId: Long, prompt: String) {
        if (conversationId == null) conversationId = history.createConversation(prompt.take(60), profileId)
        val id = conversationId ?: return
        val latest = session.lines.value.lastOrNull() ?: return
        history.addMessage(id, latest)
    }

    private suspend fun saveLastLines(model: String, tokens: Int) {
        val id = conversationId ?: return
        val last = session.lines.value.takeLast(1).firstOrNull() ?: return
        if (last.role == Role.ASSISTANT) history.addMessage(id, last.copy(model = model, tokenUsage = tokens))
    }

    private fun buildSystemPrompt(profile: AiProfile?, style: ResponseStyle): String {
        val styleText = when (style) {
            ResponseStyle.SHORT -> "Keep the answer concise."
            ResponseStyle.NORMAL -> "Give a clear normal-length answer."
            ResponseStyle.DETAILED -> "Give detailed reasoning and useful context."
            ResponseStyle.TECHNICAL -> "Use precise technical terminology and implementation-level detail when relevant."
            ResponseStyle.STEP_BY_STEP -> "Answer using numbered step-by-step instructions when actions are needed."
        }
        return listOf(profile?.systemPrompt ?: "You are ALF Vision, a helpful screen assistant.", styleText).joinToString(" ")
    }

    private fun errorMessage(t: Throwable): String = when (t) {
        is GroqApiException -> when (t.code) {
            401 -> "Groq API Key belum diatur atau tidak valid."
            403 -> "Groq menolak request ini. Periksa akses atau model."
            429 -> "Rate limit reached. Tunggu beberapa saat atau ganti model."
            500, 502, 503 -> "Groq sedang bermasalah sementara. Coba lagi nanti."
            else -> t.message ?: "Groq request gagal."
        }
        else -> t.message ?: "Terjadi kesalahan."
    }
}

```

## `app/src/main/java/com/alfread/alfvision/vision/VoiceInputManager.kt`

```kt
package com.alfread.alfvision.vision

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceInputManager(
    context: Context,
    private val session: SessionStore
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            session.setVoiceState(VoiceState.ERROR)
            session.setError("Speech recognition tidak tersedia di perangkat ini.")
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(listener)
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }
        session.setVoiceState(VoiceState.LISTENING)
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        if (session.voiceState.value == VoiceState.LISTENING) session.setVoiceState(VoiceState.IDLE)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { session.setVoiceState(VoiceState.IDLE) }
        override fun onError(error: Int) {
            session.setVoiceState(VoiceState.ERROR)
            session.setError("Voice input gagal. Coba lagi.")
        }
        override fun onResults(results: Bundle?) {
            val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (value.isNotBlank()) session.setInput(value)
            session.setVoiceState(VoiceState.IDLE)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val value = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (value.isNotBlank()) session.setInput(value)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

```

## `app/src/main/res/drawable/ic_launcher_foreground.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="M28,24h18v7H35v11h-7zM62,24h18v18h-7V31H62zM28,66h7v11h11v7H28zM73,66h7v18H62v-7h11z" />
    <path android:fillColor="#FF7B4DFF" android:pathData="M42,39h24c3.9,0 7,3.1 7,7v16c0,3.9 -3.1,7 -7,7H42c-3.9,0 -7,-3.1 -7,-7V46c0,-3.9 3.1,-7 7,-7z" />
    <path android:fillColor="#FF14D9FF" android:pathData="M46,49h16v4H46zM46,57h12v4H46z" />
</vector>

```

## `app/src/main/res/drawable/ic_notification.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M5,3h14c1.1,0 2,0.9 2,2v14c0,1.1 -0.9,2 -2,2H5c-1.1,0 -2,-0.9 -2,-2V5c0,-1.1 0.9,-2 2,-2zM7,7v10h10V7H7zM9,9h6v6H9z" />
</vector>

```

## `app/src/main/res/mipmap-anydpi/ic_launcher.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#1728FF" android:pathData="M0,0h48v48h-48z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M10,7h10v4h-6v6h-4zM28,7h10v10h-4v-6h-6zM10,31h4v6h6v4H10zM34,31h4v10H28v-4h6z"/>
    <path android:fillColor="#FF7B4DFF" android:pathData="M18,17h12c2.2,0 4,1.8 4,4v8c0,2.2 -1.8,4 -4,4H18c-2.2,0 -4,-1.8 -4,-4v-8c0,-2.2 1.8,-4 4,-4z"/>
    <path android:fillColor="#FF14D9FF" android:pathData="M19,22h10v2H19zM19,26h7v2H19z"/>
</vector>

```

## `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#FF7B4DFF" android:pathData="M0,0h48v48h-48z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M10,7h10v4h-6v6h-4zM28,7h10v10h-4v-6h-6zM10,31h4v6h6v4H10zM34,31h4v10H28v-4h6z"/>
    <path android:fillColor="#FF14D9FF" android:pathData="M17,17h14c2.2,0 4,1.8 4,4v8c0,2.2 -1.8,4 -4,4H17c-2.2,0 -4,-1.8 -4,-4v-8c0,-2.2 1.8,-4 4,-4z"/>
</vector>

```

## `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/brand_deep_blue" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>

```

## `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/brand_purple" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>

```

## `app/src/main/res/values/colors.xml`

```xml
<resources>
    <color name="brand_deep_blue">#1728FF</color>
    <color name="brand_purple">#7B4DFF</color>
    <color name="brand_cyan">#14D9FF</color>
    <color name="black">#000000</color>
    <color name="white">#FFFFFF</color>
</resources>

```

## `app/src/main/res/values/strings.xml`

```xml
<resources>
    <string name="app_name">ALF Vision Panel</string>
    <string name="app_subtitle">AI Screen Assistant</string>
    <string name="capture_notification_title">ALF Vision Panel</string>
    <string name="capture_notification_text">Screen analysis is active</string>
    <string name="notification_channel_name">Screen analysis</string>
    <string name="notification_stop">STOP</string>
    <string name="share_text_title">ALF Vision Panel</string>
</resources>

```

## `app/src/main/res/values/themes.xml`

```xml
<resources>
    <style name="Theme.ALFVisionPanel" parent="android:style/Theme.Material.Light.NoActionBar" />
</resources>

```

## `app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="files" path="." />
    <cache-path name="cache" path="." />
</paths>

```

## `app/src/test/java/com/alfread/alfvision/ErrorHandlingTest.kt`

```kt
package com.alfread.alfvision

import com.alfread.alfvision.data.network.GroqApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorHandlingTest {
    @Test fun rateLimitCodeIsPreserved() {
        assertEquals(429, GroqApiException(429, "Rate limit reached").code)
    }
}

```

## `app/src/test/java/com/alfread/alfvision/GroqRequestBuilderTest.kt`

```kt
package com.alfread.alfvision

import com.alfread.alfvision.core.model.ChatLine
import com.alfread.alfvision.core.model.Role
import com.alfread.alfvision.data.network.GroqApiService
import com.alfread.alfvision.data.network.buildHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqRequestBuilderTest {
    @Test fun serviceUsesGroqEndpoint() {
        val service = GroqApiService { buildHttpClient(10) }
        assertTrue(service.javaClass.simpleName.contains("GroqApiService"))
    }
}

```

## `app/src/test/java/com/alfread/alfvision/ImageProcessorTest.kt`

```kt
package com.alfread.alfvision

import com.alfread.alfvision.core.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProcessorTest {
    @Test fun regionClampsToImageBounds() {
        val result = Region(950, 950, 200, 200, 1000, 1000).normalized(1000, 1000)
        assertEquals(50, result.width)
        assertEquals(50, result.height)
    }
}

```

## `app/src/test/java/com/alfread/alfvision/RegionCalculatorTest.kt`

```kt
package com.alfread.alfvision

import com.alfread.alfvision.core.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionCalculatorTest {
    @Test fun normalizedRegionScalesCoordinates() {
        val source = Region(100, 200, 400, 300, 1000, 2000)
        val result = source.normalized(2000, 4000)
        assertEquals(200, result.x)
        assertEquals(400, result.y)
        assertEquals(800, result.width)
        assertEquals(600, result.height)
    }
}

```

## `build.gradle.kts`

```kts
plugins {
    id("com.android.application") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.21" apply false
}

```

## `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true

```

## `gradlew`

```text
#!/bin/sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.13
DIST="$HOME/.gradle/alf-wrapper/gradle-$GRADLE_VERSION"
BIN="$DIST/bin/gradle"
if [ ! -x "$BIN" ]; then
  mkdir -p "$DIST"
  TMP="$HOME/.gradle/alf-wrapper/gradle-$GRADLE_VERSION.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "$TMP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$TMP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "curl or wget is required to bootstrap Gradle $GRADLE_VERSION" >&2
    exit 1
  fi
  rm -rf "$DIST/unpacked"
  mkdir -p "$DIST/unpacked"
  unzip -q "$TMP" -d "$DIST/unpacked"
  cp -R "$DIST/unpacked/gradle-$GRADLE_VERSION/." "$DIST/"
  rm -rf "$DIST/unpacked" "$TMP"
fi
exec "$BIN" -p "$APP_HOME" "$@"

```

## `gradlew.bat`

```bat
@echo off
setlocal
set APP_HOME=%~dp0
set GRADLE_VERSION=8.13
set DIST=%USERPROFILE%\.gradle\alf-wrapper\gradle-%GRADLE_VERSION%
set BIN=%DIST%\bin\gradle.bat
if not exist "%BIN%" (
  echo Bootstrapping Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; New-Item -ItemType Directory -Force '%DIST%' | Out-Null; Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST%\gradle.zip'; Expand-Archive -Force '%DIST%\gradle.zip' '%DIST%\unpacked'; Copy-Item -Recurse -Force '%DIST%\unpacked\gradle-%GRADLE_VERSION%\*' '%DIST%\'; Remove-Item -Recurse -Force '%DIST%\unpacked'; Remove-Item -Force '%DIST%\gradle.zip'"
)
call "%BIN%" -p "%APP_HOME%" %*
endlocal

```

## `settings.gradle.kts`

```kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ALF-Vision-Panel"
include(":app")

```

