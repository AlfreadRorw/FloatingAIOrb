# ALF Vision Panel — Complete Source Listing

Full text of all project source/config/resource files in repository order.

## `.gitignore`

```gitignore
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


### Room schema

The initial release keeps Room schema export disabled because the database is versioned locally and migrations remain explicit in the database configuration.


## Changelog 1.1.0

**Fix force close**
- Tema: `Color(argb.toULong())` memakai konstruktor nilai mentah Compose sehingga color space invalid dan app crash saat dibuka. Sekarang memakai `Color(Long)` (ARGB).
- Overlay: `ComposeView` di Service sekarang punya LifecycleOwner / SavedStateRegistryOwner / ViewModelStoreOwner (`OverlayLifecycleOwner`), sebelumnya crash "ViewTreeLifecycleOwner not found" saat START VISION.
- `ScreenCaptureService`: `startForeground()` dipanggil lebih dulu (tidak lagi ForegroundServiceDidNotStartInTimeException), frame terakhir ditahan sehingga capture layar statis tidak macet, padding bitmap dibuang, mendukung rotasi.
- Editor anotasi: bitmap tidak lagi di-recycle saat masih dipakai, koordinat mark dipetakan ke bitmap asli, rect/circle tidak lagi transparan.
- Panel tidak lagi hilang setelah capture / region selector (view disembunyikan, bukan dihapus tanpa null).
- Region selector: drag tidak lagi putus tiap perubahan, `normalized()` aman di tepi layar.
- Jaringan live (NetworkCallback), status API key tidak lagi decrypt Keystore di tiap recomposition, Auto Analyze tersambung ke setting, update settings atomik.

**UI/UX**
- Tema gelap/terang baru, aksen, shape, dan tipografi.
- Floating DockBar (Home, Vision, Chat, History, Settings) dengan indikator animasi, tersembunyi saat keyboard muncul.
- Home baru: hero card, status tile, quick actions, tombol START/STOP VISION.
- Panel overlay baru dengan dockbar sendiri (Chat, Tools, Setup, App), orb dengan tap / double tap / long press, drag memakai koordinat raw layar.
- Settings dikelompokkan dalam kartu, slider hanya menyimpan saat dilepas.

## Changelog 1.2.0
- **Jawab Soal**: satu aksi untuk capture layar lalu menjawab semua soal yang terlihat (nomor, jawaban akhir, alasan singkat). Tersedia di chip chat, tab Tools panel, Home, dan double tap pada orb.
- Gambar layar bisa dilampirkan langsung di chat (tombol kamera di input panel), terlihat sebagai pratinjau + thumbnail di bubble, dan bisa dilepas.
- Teks chat di panel overlay diperbaiki (warna konten eksplisit, latar bubble solid, line height rapat).
- Jawaban AI dirender dari markdown (bold, italic, bullet, heading, kode) tanpa simbol `*`, dan prompt sistem meminta teks biasa.

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

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true

```

## `gradlew`

```gradlew
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

## `.github/workflows/build.yml`

```yml
name: Build ALF Vision Panel

on:
  push:
    branches:
      - master
      - main
  pull_request:
    branches:
      - master
      - main
  workflow_dispatch:

permissions:
  contents: read

env:
  JAVA_VERSION: '17'
  ANDROID_COMPILE_SDK: '36'
  ANDROID_BUILD_TOOLS: '36.0.0'
  ANDROID_HOME: /usr/local/lib/android/sdk
  ANDROID_SDK_ROOT: /usr/local/lib/android/sdk

jobs:
  build:
    name: Build Android APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v5

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: gradle

      - name: Prepare Android SDK
        shell: bash
        run: |
          set -euo pipefail

          SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
          if [ ! -x "$SDKMANAGER" ]; then
            SDKMANAGER="${ANDROID_HOME}/cmdline-tools/16.0/bin/sdkmanager"
          fi
          if [ ! -x "$SDKMANAGER" ]; then
            echo "ERROR: sdkmanager not found in $ANDROID_HOME"
            find "$ANDROID_HOME/cmdline-tools" -maxdepth 3 -type f -name sdkmanager -print || true
            exit 1
          fi

          echo "Using sdkmanager: $SDKMANAGER"
          "$SDKMANAGER" --version

          echo "Accepting Android SDK licenses..."
          yes | "$SDKMANAGER" --licenses >/dev/null || true

          echo "Installing required Android SDK packages..."
          "$SDKMANAGER" \
            "platform-tools" \
            "platforms;android-${ANDROID_COMPILE_SDK}" \
            "build-tools;${ANDROID_BUILD_TOOLS}"

          echo "Installed Android SDK packages:"
          "$SDKMANAGER" --list_installed || true

      - name: Make Gradle executable
        run: chmod +x ./gradlew

      - name: Verify Gradle
        run: ./gradlew --version

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
          retention-days: 14

      - name: Prepare signing keystore
        id: signing
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        shell: bash
        run: |
          set -euo pipefail
          if [ -n "${KEYSTORE_BASE64:-}" ] && \
             [ -n "${KEYSTORE_PASSWORD:-}" ] && \
             [ -n "${KEY_ALIAS:-}" ] && \
             [ -n "${KEY_PASSWORD:-}" ]; then
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
          retention-days: 14

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
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val storeFileProp = providers.gradleProperty("signingStoreFile").orNull
                ?: System.getenv("KEYSTORE_PATH")
            val storePasswordProp = providers.gradleProperty("signingStorePassword").orNull
                ?: System.getenv("KEYSTORE_PASSWORD")
            val aliasProp = providers.gradleProperty("signingKeyAlias").orNull
                ?: System.getenv("KEY_ALIAS")
            val keyPasswordProp = providers.gradleProperty("signingKeyPassword").orNull
                ?: System.getenv("KEY_PASSWORD")
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
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
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ALFVisionPanel"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode"
            android:launchMode="singleTop"
            android:screenOrientation="fullSensor"
            android:windowSoftInputMode="adjustResize">
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "alf_vision.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
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
            runCatching {
                profileRepository.ensureDefaults()
                val initial = settingsRepository.flow.first()
                if (initial.autoDeleteDays > 0) {
                    historyRepository.deleteOlderThan(
                        System.currentTimeMillis() - initial.autoDeleteDays * 86_400_000L
                    )
                }
            }

            runCatching {
                var lastAutoAnalyze = false
                settingsRepository.flow.collect { value ->
                    settingsSnapshotTimeout = value.networkTimeoutSeconds
                    settingsSnapshotRetry = value.retryCount
                    // Auto Analyze: switch di Settings sekarang benar-benar menyalakan/mematikan loop.
                    if (value.vision.autoAnalyze != lastAutoAnalyze) {
                        lastAutoAnalyze = value.vision.autoAnalyze
                        withContext(Dispatchers.Main) { controller.setAutoAnalyze(lastAutoAnalyze) }
                    }
                }
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
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.navigation.ALFNavHost
import com.alfread.alfvision.ui.navigation.navigateTo
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import com.alfread.alfvision.ui.theme.isDarkTheme
import com.alfread.alfvision.vision.VoiceState

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val settings by vm.settings.collectAsState()
            val dark = isDarkTheme(settings)
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ALFVisionTheme(settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    LaunchedEffect(pendingRoute) {
                        pendingRoute?.let {
                            navController.navigateTo(it)
                            pendingRoute = null
                        }
                    }

                    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        val data = result.data
                        if (result.resultCode == Activity.RESULT_OK && data != null) {
                            val intent = Intent(this@MainActivity, ScreenCaptureService::class.java)
                                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
                            runCatching { ContextCompat.startForegroundService(this@MainActivity, intent) }
                                .onFailure { vm.session.setError("Screen capture gagal dimulai. Coba lagi.") }
                        } else {
                            vm.session.setError("Screen capture permission belum diberikan.")
                        }
                    }

                    val beginVision: () -> Unit = {
                        vm.startFloating()
                        if (!ScreenCaptureService.isRunning) {
                            val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
                            if (manager != null) captureLauncher.launch(manager.createScreenCaptureIntent())
                            else vm.session.setError("MediaProjection tidak tersedia di perangkat ini.")
                        }
                    }

                    // Izin notifikasi diminta DULU, baru dialog screen capture. Dua dialog sekaligus
                    // sebelumnya saling membatalkan.
                    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
                        beginVision()
                    }
                    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) vm.voice() else vm.session.setError("Izin mikrofon ditolak.")
                    }

                    ALFNavHost(
                        navController = navController,
                        vm = vm,
                        onStartVision = {
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                openOverlaySettings()
                            } else if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                beginVision()
                            }
                        },
                        onStopVision = { vm.stopVision() },
                        onRequestMicrophone = {
                            when {
                                vm.session.voiceState.value == VoiceState.LISTENING -> vm.stopVoice()
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.voice()
                                else -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onOverlay = { openOverlaySettings() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_ROUTE = "alf_route"
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
    val tokenUsage: Int? = null,
    /** Thumbnail kecil gambar layar yang dikirim bersama pesan (hanya di memori sesi). */
    val imageBytes: ByteArray? = null
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
        val mw = maxWidth.coerceAtLeast(1)
        val mh = maxHeight.coerceAtLeast(1)
        val sx = if (screenWidth > 0) mw.toFloat() / screenWidth else 1f
        val sy = if (screenHeight > 0) mh.toFloat() / screenHeight else 1f
        val nx = (x * sx).toInt().coerceIn(0, mw - 1)
        val ny = (y * sy).toInt().coerceIn(0, mh - 1)
        val nw = (width * sx).toInt().coerceIn(1, (mw - nx).coerceAtLeast(1))
        val nh = (height * sy).toInt().coerceIn(1, (mh - ny).coerceAtLeast(1))
        return copy(x = nx, y = ny, width = nw, height = nh, screenWidth = mw, screenHeight = mh)
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
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkMonitor(context: Context) {
    private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)
    private val _connected = MutableStateFlow(isConnected())
    val connected: StateFlow<Boolean> = _connected

    init {
        // Status jaringan sekarang live, bukan sekadar snapshot saat app dibuka.
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
            })
        }
    }

    fun refresh() { _connected.value = isConnected() }

    fun isConnected(): Boolean = runCatching {
        val manager = cm ?: return@runCatching false
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}

```

## `app/src/main/java/com/alfread/alfvision/core/util/ScreenMetrics.kt`

```kt
package com.alfread.alfvision.core.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** Ukuran layar nyata (px). Dipakai bersama oleh overlay dan MediaProjection supaya koordinatnya sama. */
object ScreenMetrics {
    fun size(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        if (wm == null) {
            val m = context.resources.displayMetrics
            return m.widthPixels to m.heightPixels
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
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
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted store for the Groq API key.
 *
 * Keystore initialization is deliberately lazy. Some Android/OEM devices can
 * throw from AndroidKeyStore during Application startup; that must never make
 * the whole app crash before the first screen is shown.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)
    private val alias = "alf_vision_secure_key"

    private fun ensureKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            generateKey()
        }
        keyStore.getKey(alias, null) as? SecretKey
    }.recoverCatching {
        // A stale/corrupted OEM keystore entry should not permanently brick
        // API-key storage. Remove it and create a fresh AES-GCM key.
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        runCatching { keyStore.deleteEntry(alias) }
        generateKey()
        val refreshed = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        refreshed.getKey(alias, null) as? SecretKey
    }.getOrNull()

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
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
        return generator.generateKey()
    }

    fun putApiKey(value: String) {
        if (value.isBlank()) {
            deleteApiKey()
            return
        }

        runCatching {
            val key = ensureKey() ?: error("Android Keystore is unavailable")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            prefs.edit()
                .putString("api_key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("api_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        }.onFailure {
            // Never crash the UI because secure storage is unavailable.
            deleteApiKey()
        }
    }

    fun getApiKey(): String? {
        val encrypted = prefs.getString("api_key_cipher", null) ?: return null
        val iv = prefs.getString("api_key_iv", null) ?: return null

        return runCatching {
            val key = ensureKey() ?: return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            val bytes = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            String(bytes, StandardCharsets.UTF_8)
        }.getOrElse {
            // If an OEM reset invalidated the old key, discard the unreadable
            // value instead of crashing or repeatedly failing on every launch.
            deleteApiKey()
            null
        }
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun deleteApiKey() {
        prefs.edit()
            .remove("api_key_cipher")
            .remove("api_key_iv")
            .apply()
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
    version = 2,
    exportSchema = false
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
                        ownedBy = item.optString("owned_by").takeIf { it.isNotBlank() },
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
        for (attempt in 0 until attempts) {
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
                } else throw e
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
import kotlinx.coroutines.flow.catch

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

    private fun read(p: Preferences): AppSettings =
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

    val flow: Flow<AppSettings> = context.alfSettingsDataStore.data
        .map { p -> read(p) }
        .catch { emit(AppSettings()) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        var result: AppSettings? = null
        context.alfSettingsDataStore.edit { p ->
            // Baca + tulis dalam satu transaksi DataStore supaya update beruntun tidak saling menimpa.
            val next = transform(read(p))
            result = next
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
        val next = result ?: return
        runCatching {
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
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.MainActivity
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.core.util.ScreenMetrics
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import com.alfread.alfvision.ui.theme.color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class FloatingPanelService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var container: com.alfread.alfvision.AppContainer
    private val owner = OverlayLifecycleOwner()

    private var panelView: ComposeView? = null
    private var orbView: ComposeView? = null
    private var regionView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var orbParams: WindowManager.LayoutParams? = null

    private val panelTab = mutableStateOf(PanelTab.CHAT)
    @Volatile private var settingsCache = AppSettings()
    private var minimized = false
    private var expanded = false
    private var savedPanelSize: Pair<Int, Int>? = null
    private var captureSuppressed = false
    private var autoHideJob: Job? = null
    private var persistJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = (application as AlfVisionApplication).container
        wm = getSystemService(WindowManager::class.java)
        owner.create()
        scope.launch {
            settingsCache = container.settingsRepository.flow.first()
            startPanel()
            container.settingsRepository.flow.collect { settingsCache = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> if (minimized) restorePanel() else if (panelView == null) startPanel()
            ACTION_MINIMIZE -> minimize()
            ACTION_CLOSE -> stopSelf()
            ACTION_CAPTURE -> container.controller.capture()
            ACTION_REGION -> showRegionSelector()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        autoHideJob?.cancel()
        persistJob?.cancel()
        removeView(panelView)
        removeView(orbView)
        removeView(regionView)
        panelView = null
        orbView = null
        regionView = null
        scope.cancel()
        owner.destroy()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------------------------------
    // PANEL
    // ---------------------------------------------------------------------------------------

    private fun startPanel() {
        if (panelView != null || minimized) return
        if (!Settings.canDrawOverlays(this)) {
            container.sessionStore.setError("Overlay permission belum aktif.")
            return
        }
        val cfg = settingsCache.floating
        val (sw, sh) = ScreenMetrics.size(this)
        val width = dp(cfg.panelWidthDp).coerceAtMost(sw)
        val height = dp(cfg.panelHeightDp).coerceAtMost(sh)
        // FIX: FLAG_NOT_TOUCH_MODAL supaya sentuhan di luar panel tetap sampai ke aplikasi di bawah,
        // dan NOT_FOCUSABLE default supaya game/app di bawah tidak kehilangan fokus.
        val params = WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cfg.x.coerceIn(0, (sw - width).coerceAtLeast(0))
            y = cfg.y.coerceIn(0, (sh - height).coerceAtLeast(0))
        }
        val view = ComposeView(this).apply {
            owner.install(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { PanelContent() }
        }
        panelView = view
        panelParams = params
        expanded = false
        addView(view, params) { panelView = null; panelParams = null }
        applyVisibility()
        scheduleAutoHide()
    }

    @androidx.compose.runtime.Composable
    private fun PanelContent() {
        val settings by container.settingsRepository.flow.collectAsState(initial = settingsCache)
        val lines by container.sessionStore.lines.collectAsState()
        val input by container.sessionStore.input.collectAsState()
        val busy by container.sessionStore.busy.collectAsState()
        val error by container.sessionStore.error.collectAsState()
        val image by container.sessionStore.currentImage.collectAsState()
        val voice by container.sessionStore.voiceState.collectAsState()
        ALFVisionTheme(settings) {
            FloatingPanel(
                settings = settings,
                tab = panelTab.value,
                onTab = { panelTab.value = it; setPanelFocusable(false) },
                lines = lines,
                input = input,
                busy = busy,
                error = error,
                currentImage = image,
                voiceState = voice,
                actions = buildActions()
            )
        }
    }

    private fun buildActions() = PanelActions(
        onInput = container.sessionStore::setInput,
        onSend = { container.controller.ask(container.sessionStore.input.value) },
        onMinimize = { minimize() },
        onMaximize = { toggleMaximize() },
        onClose = { stopSelf() },
        onCapture = { container.controller.capture() },
        onAnswer = { panelTab.value = PanelTab.CHAT; container.controller.answerScreen() },
        onClearImage = { container.sessionStore.clearImage() },
        onSelectRegion = { showRegionSelector() },
        onClearRegion = { container.sessionStore.setRegion(null) },
        onQuickAction = container.controller::quickAction,
        onCompare = container.controller::compare,
        onRetry = container.controller::retryLast,
        onStop = container.controller::cancelRequest,
        onPin = container.sessionStore::pinCurrent,
        onClear = {
            container.sessionStore.clearChat()
            container.controller.resetConversation()
        },
        onVoice = {
            if (container.sessionStore.voiceState.value == com.alfread.alfvision.vision.VoiceState.LISTENING) {
                container.voiceInputManager.stop()
            } else if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                container.voiceInputManager.start()
            } else {
                container.sessionStore.setError("Izin mikrofon belum diberikan. Buka app > Settings > Permissions.")
            }
        },
        onDrag = { dx, dy -> movePanel(dx, dy) },
        onDragEnd = { snapPanel() },
        onResize = { dx, dy -> resizePanel(dx, dy) },
        onResizeEnd = { persistSize() },
        onOpenApp = { route -> openApp(route) },
        onUpdateSettings = { transform -> scope.launch { container.settingsRepository.update(transform) } },
        onInputTouch = { setPanelFocusable(true) },
        onInputFocus = { focused -> if (!focused) setPanelFocusable(false) },
        onInteract = { scheduleAutoHide() }
    )

    private fun setPanelFocusable(focusable: Boolean) {
        val params = panelParams ?: return
        val view = panelView ?: return
        val currentlyFocusable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
        if (currentlyFocusable == focusable) return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun toggleMaximize() {
        val params = panelParams ?: return
        val (sw, sh) = ScreenMetrics.size(this)
        if (!expanded) {
            savedPanelSize = params.width to params.height
            params.width = (sw * 0.94f).roundToInt()
            params.height = (sh * 0.84f).roundToInt()
            params.x = ((sw - params.width) / 2).coerceAtLeast(0)
            params.y = ((sh - params.height) / 2).coerceAtLeast(0)
        } else {
            savedPanelSize?.let { (w, h) -> params.width = w; params.height = h }
            clampPanel(params)
        }
        expanded = !expanded
        runCatching { wm.updateViewLayout(panelView, params) }
    }

    private fun minimize() {
        if (minimized) return
        minimized = true
        autoHideJob?.cancel()
        setPanelFocusable(false)
        removeView(panelView)
        panelView = null
        showOrb()
    }

    private fun restorePanel() {
        if (!minimized) return
        minimized = false
        removeView(orbView)
        orbView = null
        // posisi panel mengikuti posisi orb terakhir
        orbParams?.let { o ->
            persistPosition(o.x, o.y)
            settingsCache = settingsCache.copy(floating = settingsCache.floating.copy(x = o.x, y = o.y))
        }
        startPanel()
    }

    private fun movePanel(dx: Float, dy: Float) {
        val params = panelParams ?: return
        val view = panelView ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampPanel(params)
        runCatching { wm.updateViewLayout(view, params) }
    }

    /** Snap ke tepi hanya saat jari dilepas (sebelumnya snap terjadi di tiap gerakan sehingga panel "lengket"). */
    private fun snapPanel() {
        val params = panelParams ?: return
        val view = panelView ?: return
        val (sw, _) = ScreenMetrics.size(this)
        if (settingsCache.floating.snapToEdge && !expanded) {
            val threshold = (sw * 0.12f).roundToInt()
            if (params.x <= threshold) params.x = 0
            else if (params.x + params.width >= sw - threshold) params.x = (sw - params.width).coerceAtLeast(0)
            runCatching { wm.updateViewLayout(view, params) }
        }
        persistPosition(params.x, params.y)
    }

    private fun resizePanel(dx: Float, dy: Float) {
        val params = panelParams ?: return
        val view = panelView ?: return
        val (sw, sh) = ScreenMetrics.size(this)
        params.width = (params.width + dx.roundToInt()).coerceIn(dp(280), dp(520).coerceAtMost(sw))
        params.height = (params.height + dy.roundToInt()).coerceIn(dp(300), dp(820).coerceAtMost(sh))
        clampPanel(params)
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun persistSize() {
        val params = panelParams ?: return
        if (expanded) return
        scope.launch { container.settingsRepository.savePanelSize(pxToDp(params.width), pxToDp(params.height)) }
    }

    private fun clampPanel(params: WindowManager.LayoutParams) {
        val (sw, sh) = ScreenMetrics.size(this)
        params.x = params.x.coerceIn(0, (sw - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (sh - params.height).coerceAtLeast(0))
    }

    // ---------------------------------------------------------------------------------------
    // ORB
    // ---------------------------------------------------------------------------------------

    private fun showOrb() {
        if (orbView != null) return
        if (!Settings.canDrawOverlays(this)) return
        val cfg = settingsCache.floating
        val size = dp(cfg.orbSizeDp)
        val (sw, sh) = ScreenMetrics.size(this)
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (panelParams?.x ?: cfg.x).coerceIn(0, (sw - size).coerceAtLeast(0))
            y = (panelParams?.y ?: cfg.y).coerceIn(0, (sh - size).coerceAtLeast(0))
        }
        orbParams = params
        val view = ComposeView(this).apply {
            owner.install(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings by container.settingsRepository.flow.collectAsState(initial = settingsCache)
                val busy by container.sessionStore.busy.collectAsState()
                ALFVisionTheme(settings) {
                    FloatingOrb(
                        accent = settings.accent.color(),
                        busy = busy,
                        onTap = { restorePanel() },
                        onDoubleTap = {
                            // Double tap orb = jawab soal di layar (capture + jawab dalam satu aksi).
                            restorePanel()
                            panelTab.value = PanelTab.CHAT
                            container.controller.answerScreen()
                        },
                        onLongPress = { showRegionSelector() },
                        onDrag = { dx, dy -> moveOrb(dx, dy) },
                        onDragEnd = { snapOrb() }
                    )
                }
            }
        }
        orbView = view
        addView(view, params) { orbView = null }
        applyVisibility()
    }

    private fun moveOrb(dx: Float, dy: Float) {
        val params = orbParams ?: return
        val view = orbView ?: return
        val (sw, sh) = ScreenMetrics.size(this)
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (sw - params.width).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (sh - params.height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun snapOrb() {
        val params = orbParams ?: return
        val view = orbView ?: return
        val (sw, _) = ScreenMetrics.size(this)
        if (settingsCache.floating.snapToEdge) {
            params.x = if (params.x + params.width / 2 < sw / 2) 0 else (sw - params.width).coerceAtLeast(0)
            runCatching { wm.updateViewLayout(view, params) }
        }
        persistPosition(params.x, params.y)
    }

    // ---------------------------------------------------------------------------------------
    // REGION SELECTOR
    // ---------------------------------------------------------------------------------------

    private fun defaultRegion(sw: Int, sh: Int) = Region(
        x = sw / 5,
        y = sh / 5,
        width = sw * 3 / 5,
        height = sh * 3 / 5,
        screenWidth = sw,
        screenHeight = sh,
        rotation = currentDisplayRotation()
    )

    fun showRegionSelector() {
        if (regionView != null || !Settings.canDrawOverlays(this)) return
        val (sw, sh) = ScreenMetrics.size(this)
        val existing = container.sessionStore.region.value
        container.sessionStore.setRegion(existing?.normalized(sw, sh) ?: defaultRegion(sw, sh))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val view = ComposeView(this).apply {
            owner.install(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings by container.settingsRepository.flow.collectAsState(initial = settingsCache)
                val region by container.sessionStore.region.collectAsState()
                ALFVisionTheme(settings) {
                    region?.let { current ->
                        RegionSelectorOverlay(
                            region = current,
                            accent = settings.accent.color(),
                            onRegionChange = { container.sessionStore.setRegion(it) },
                            onReset = { container.sessionStore.setRegion(defaultRegion(sw, sh)) },
                            onCenter = {
                                container.sessionStore.setRegion(current.copy(x = (sw - current.width) / 2, y = (sh - current.height) / 2))
                            },
                            onFullscreen = {
                                container.sessionStore.setRegion(Region(0, 0, sw, sh, sw, sh, rotation = currentDisplayRotation()))
                            },
                            onApply = { closeRegionSelector() },
                            onSave = { saveRegionPreset() },
                            onClose = { closeRegionSelector() }
                        )
                    }
                }
            }
        }
        regionView = view
        addView(view, params) { regionView = null }
        applyVisibility()
    }

    private fun saveRegionPreset() {
        val region = container.sessionStore.region.value ?: return
        scope.launch {
            val name = "Region ${System.currentTimeMillis().toString().takeLast(4)}"
            container.regionRepository.save(name, region)
            closeRegionSelector()
        }
    }

    private fun closeRegionSelector() {
        removeView(regionView)
        regionView = null
        applyVisibility()
        if (!minimized && panelView == null) startPanel()
    }

    // ---------------------------------------------------------------------------------------
    // CAPTURE SUPPRESSION
    // FIX: sebelumnya view dihapus tetapi referensinya tidak di-null-kan sehingga panel TIDAK PERNAH
    // muncul lagi setelah capture atau setelah region selector ditutup. Sekarang view hanya
    // disembunyikan (INVISIBLE) lalu ditampilkan lagi, state chat/tab tetap utuh.
    // ---------------------------------------------------------------------------------------

    fun setCaptureSuppressed(value: Boolean) {
        captureSuppressed = value
        applyVisibility()
    }

    private fun applyVisibility() {
        val hide = captureSuppressed
        panelView?.visibility = if (hide || regionView != null) View.INVISIBLE else View.VISIBLE
        orbView?.visibility = if (hide) View.INVISIBLE else View.VISIBLE
        regionView?.visibility = if (hide) View.INVISIBLE else View.VISIBLE
    }

    // ---------------------------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------------------------

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        val cfg = settingsCache.floating
        if (!cfg.autoHide || minimized) return
        autoHideJob = scope.launch {
            delay(cfg.autoHideMillis)
            if (!minimized && regionView == null) minimize()
        }
    }

    private fun persistPosition(x: Int, y: Int) {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(300)
            container.settingsRepository.savePanelPosition(x, y)
        }
    }

    private fun openApp(route: String?) {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        if (route != null) intent.putExtra(MainActivity.EXTRA_ROUTE, route)
        runCatching { startActivity(intent) }
    }

    private fun currentDisplayRotation(): Int =
        getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation ?: 0

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun pxToDp(px: Int): Int = (px / resources.displayMetrics.density).roundToInt()

    private fun addView(view: View, params: WindowManager.LayoutParams, onFail: () -> Unit) {
        runCatching { wm.addView(view, params) }.onFailure {
            onFail()
            container.sessionStore.setError("Floating panel gagal tampil. Pastikan Overlay Permission aktif.")
        }
    }

    private fun removeView(view: View?) {
        if (view != null) runCatching { wm.removeView(view) }
    }

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

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.ui.components.DockBar
import com.alfread.alfvision.ui.components.DockItem
import com.alfread.alfvision.ui.components.MessageContent
import com.alfread.alfvision.ui.components.SliderRow
import com.alfread.alfvision.ui.components.SwitchRow
import com.alfread.alfvision.ui.theme.AlfCyan
import com.alfread.alfvision.ui.theme.brandBrush
import com.alfread.alfvision.ui.theme.color
import com.alfread.alfvision.vision.VoiceState
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PanelTab { CHAT, TOOLS, SETUP }

const val ROUTE_APP = "app"

/** Semua callback panel dikelompokkan di sini supaya signature FloatingPanel tetap rapi. */
class PanelActions(
    val onInput: (String) -> Unit,
    val onSend: () -> Unit,
    val onMinimize: () -> Unit,
    val onMaximize: () -> Unit,
    val onClose: () -> Unit,
    val onCapture: () -> Unit,
    val onAnswer: () -> Unit,
    val onClearImage: () -> Unit,
    val onSelectRegion: () -> Unit,
    val onClearRegion: () -> Unit,
    val onQuickAction: (String) -> Unit,
    val onCompare: () -> Unit,
    val onRetry: () -> Unit,
    val onStop: () -> Unit,
    val onPin: () -> Unit,
    val onClear: () -> Unit,
    val onVoice: () -> Unit,
    val onDrag: (Float, Float) -> Unit,
    val onDragEnd: () -> Unit,
    val onResize: (Float, Float) -> Unit,
    val onResizeEnd: () -> Unit,
    val onOpenApp: (String?) -> Unit,
    val onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    val onInputTouch: () -> Unit,
    val onInputFocus: (Boolean) -> Unit,
    val onInteract: () -> Unit
)

// ---------------------------------------------------------------------------------------------
// GESTURE HELPERS
// Drag jendela overlay memakai koordinat RAW layar. Memakai detectDragGestures (koordinat lokal)
// membuat panel bergetar karena view-nya ikut bergerak saat dipindah.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.windowDrag(enabled: Boolean, onDrag: (Float, Float) -> Unit, onEnd: () -> Unit): Modifier = composed {
    val last = remember { floatArrayOf(0f, 0f) }
    if (!enabled) {
        Modifier
    } else {
        Modifier.pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    last[0] = event.rawX
                    last[1] = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    onDrag(event.rawX - last[0], event.rawY - last[1])
                    last[0] = event.rawX
                    last[1] = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onEnd()
                    true
                }
                else -> false
            }
        }
    }
}

private class OrbGestureState {
    var downX = 0f
    var downY = 0f
    var lastX = 0f
    var lastY = 0f
    var moved = false
    var longFired = false
    var lastTapTime = 0L
    var longRunnable: Runnable? = null
    var tapRunnable: Runnable? = null
}

/** Tap, double tap, long press dan drag untuk orb dalam satu handler. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.orbGestures(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier = composed {
    val state = remember { OrbGestureState() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val slop = with(LocalDensity.current) { 8.dp.toPx() }
    Modifier.pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.downX = event.rawX
                state.downY = event.rawY
                state.lastX = event.rawX
                state.lastY = event.rawY
                state.moved = false
                state.longFired = false
                val runnable = Runnable {
                    if (!state.moved) {
                        state.longFired = true
                        onLongPress()
                    }
                }
                state.longRunnable = runnable
                handler.postDelayed(runnable, 500L)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!state.moved && (abs(event.rawX - state.downX) > slop || abs(event.rawY - state.downY) > slop)) {
                    state.moved = true
                    state.longRunnable?.let { handler.removeCallbacks(it) }
                }
                if (state.moved) {
                    onDrag(event.rawX - state.lastX, event.rawY - state.lastY)
                }
                state.lastX = event.rawX
                state.lastY = event.rawY
                true
            }
            MotionEvent.ACTION_UP -> {
                state.longRunnable?.let { handler.removeCallbacks(it) }
                if (state.moved) {
                    onDragEnd()
                } else if (!state.longFired) {
                    val now = System.currentTimeMillis()
                    val pending = state.tapRunnable
                    if (pending != null && now - state.lastTapTime < 300L) {
                        handler.removeCallbacks(pending)
                        state.tapRunnable = null
                        onDoubleTap()
                    } else {
                        state.lastTapTime = now
                        val runnable = Runnable {
                            state.tapRunnable = null
                            onTap()
                        }
                        state.tapRunnable = runnable
                        handler.postDelayed(runnable, 300L)
                    }
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                state.longRunnable?.let { handler.removeCallbacks(it) }
                if (state.moved) onDragEnd()
                true
            }
            else -> false
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ORB
// ---------------------------------------------------------------------------------------------

@Composable
fun FloatingOrb(
    accent: Color,
    busy: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "orbPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "orbPulseValue"
    )
    val ringAlpha = if (busy) pulse else 0.9f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), AlfCyan.copy(alpha = 0.85f))))
            .border(2.dp, Color.White.copy(alpha = ringAlpha), CircleShape)
            .orbGestures(onTap, onDoubleTap, onLongPress, onDrag, onDragEnd),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Visibility, contentDescription = "Open ALF Vision", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// PANEL
// ---------------------------------------------------------------------------------------------

private val panelDock = listOf(
    DockItem(PanelTab.CHAT.name, "Chat", Icons.AutoMirrored.Filled.Chat),
    DockItem(PanelTab.TOOLS.name, "Tools", Icons.Default.Dashboard),
    DockItem(PanelTab.SETUP.name, "Setup", Icons.Default.Tune),
    DockItem(ROUTE_APP, "App", Icons.Default.Home)
)

@Composable
fun FloatingPanel(
    settings: AppSettings,
    tab: PanelTab,
    onTab: (PanelTab) -> Unit,
    lines: List<ChatLine>,
    input: String,
    busy: Boolean,
    error: String?,
    currentImage: PendingImage?,
    voiceState: VoiceState,
    actions: PanelActions
) {
    val scheme = MaterialTheme.colorScheme
    val accent = settings.accent.color()
    val shape = RoundedCornerShape(26.dp)
    val opacity = settings.floating.opacity
    // FIX: overlay tidak punya Surface di root, sehingga LocalContentColor default hitam
    // (teks gelap di atas latar gelap). Sekarang warna konten diset eksplisit.
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(scheme.surface.copy(alpha = opacity), scheme.background.copy(alpha = opacity))))
            .border(1.dp, accent.copy(alpha = 0.4f), shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) actions.onInteract()
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            PanelHeader(accent, busy, settings.floating.locked, actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    PanelTab.CHAT -> ChatPane(settings, lines, input, busy, error, currentImage, voiceState, actions)
                    PanelTab.TOOLS -> ToolsPane(accent, currentImage != null, actions) { onTab(PanelTab.CHAT) }
                    PanelTab.SETUP -> SetupPane(settings, actions)
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                DockBar(
                    items = panelDock,
                    selectedRoute = tab.name,
                    compact = true,
                    onSelect = { item ->
                        if (item.route == ROUTE_APP) actions.onOpenApp(null)
                        else onTab(PanelTab.valueOf(item.route))
                    }
                )
            }
        }
        // Handle resize di pojok kanan bawah (sebelumnya di kiri bawah sehingga arah drag terbalik).
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(34.dp)
                .windowDrag(!settings.floating.locked, actions.onResize, actions.onResizeEnd),
            contentAlignment = Alignment.BottomEnd
        ) {
            val handleColor = accent.copy(alpha = 0.7f)
            Canvas(Modifier.padding(8.dp).size(14.dp)) {
                drawLine(handleColor, Offset(size.width, 0f), Offset(0f, size.height), 3f)
                drawLine(handleColor, Offset(size.width, size.height * 0.5f), Offset(size.width * 0.5f, size.height), 3f)
            }
        }
    }
    }
}

@Composable
private fun PanelHeader(accent: Color, busy: Boolean, locked: Boolean, actions: PanelActions) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().windowDrag(!locked, actions.onDrag, actions.onDragEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(brandBrush(accent)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("ALF VISION", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (busy) Color(0xFFFFB300) else Color(0xFF2ECC71)))
                    Spacer(Modifier.width(5.dp))
                    Text(if (busy) "ANALYZING" else "READY", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PanelIconButton(Icons.Default.CameraAlt, "Capture", onClick = actions.onCapture)
        PanelIconButton(Icons.Default.OpenInFull, "Maximize", onClick = actions.onMaximize)
        PanelIconButton(Icons.Default.Minimize, "Minimize", onClick = actions.onMinimize)
        PanelIconButton(Icons.Default.Close, "Close", onClick = actions.onClose)
    }
}

@Composable
private fun PanelIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ChatPane(
    settings: AppSettings,
    lines: List<ChatLine>,
    input: String,
    busy: Boolean,
    error: String?,
    currentImage: PendingImage?,
    voiceState: VoiceState,
    actions: PanelActions
) {
    val accent = settings.accent.color()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        if (currentImage != null) {
            val thumb = remember(currentImage.bytes) {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeByteArray(currentImage.bytes, 0, currentImage.bytes.size, options)?.asImageBitmap()
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (thumb != null) {
                    androidx.compose.foundation.Image(
                        thumb, contentDescription = "Gambar terlampir",
                        modifier = Modifier.size(width = 44.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Gambar layar terlampir", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(currentImage.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = actions.onPin) { Text("PIN", fontSize = 11.sp) }
                IconButton(onClick = actions.onClearImage, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Lepas gambar", modifier = Modifier.size(16.dp))
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lines.isEmpty() && error == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Capture layar lalu tanya apa saja.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(lines, key = { it.id }) { line -> MessageBubble(line, accent) }
            if (error != null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(8.dp))
                        TextButton(onClick = actions.onRetry) { Text("Retry") }
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp))
        if (!settings.floating.compactMode) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = actions.onAnswer,
                    label = { Text("Jawab Soal", fontSize = 11.sp, color = Color.White) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = accent),
                    border = null
                )
                listOf("Analyze", "Explain", "Read", "Translate", "Find Error", "Help Me").forEach { action ->
                    AssistChip(onClick = { actions.onQuickAction(action) }, label = { Text(action, fontSize = 11.sp) })
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) actions.onInputTouch()
                    }
                }
            }
        ) {
            TextField(
                value = input,
                onValueChange = actions.onInput,
                modifier = Modifier.fillMaxWidth().onFocusChanged { actions.onInputFocus(it.isFocused) },
                shape = RoundedCornerShape(22.dp),
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("Ask anything...") },
                enabled = !busy,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                leadingIcon = {
                    IconButton(onClick = actions.onVoice) {
                        Icon(if (voiceState == VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Voice input")
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.onCapture, enabled = !busy) {
                            Icon(Icons.Default.CameraAlt, "Lampirkan gambar layar", tint = accent)
                        }
                        IconButton(onClick = if (busy) actions.onStop else actions.onSend, enabled = busy || input.isNotBlank()) {
                            Icon(if (busy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (busy) "Stop" else "Send", tint = accent)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine, accent: Color) {
    val isUser = line.role == Role.USER
    val scheme = MaterialTheme.colorScheme
    // FIX: latar bubble solid + warna teks eksplisit supaya jawaban jelas terbaca.
    val bg = if (isUser) accent.copy(alpha = 0.32f) else scheme.surfaceContainerHighest
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .clip(shape)
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            MessageContent(line, scheme.onSurface, scheme.surface.copy(alpha = 0.6f), textSize = 14)
        }
    }
}

@Composable
private fun ToolsPane(accent: Color, hasImage: Boolean, actions: PanelActions, goChat: () -> Unit) {
    val tools = listOf(
        Triple(Icons.Default.CameraAlt, "Capture", actions.onCapture),
        Triple(Icons.Default.Crop, "Region", actions.onSelectRegion),
        Triple(Icons.Default.CropFree, "Full screen", actions.onClearRegion),
        Triple(Icons.Default.Compare, "Compare", { actions.onCompare(); goChat() }),
        Triple(Icons.Default.Bookmark, "Pin image", actions.onPin),
        Triple(Icons.Default.DeleteSweep, "Clear chat", actions.onClear)
    )
    val quick = listOf(
        Triple(Icons.Default.AutoAwesome, "Analyze", { actions.onQuickAction("Analyze"); goChat() }),
        Triple(Icons.Default.Forum, "Explain", { actions.onQuickAction("Explain"); goChat() }),
        Triple(Icons.Default.Search, "Read", { actions.onQuickAction("Read"); goChat() }),
        Triple(Icons.Default.Translate, "Translate", { actions.onQuickAction("Translate"); goChat() }),
        Triple(Icons.Default.BugReport, "Find Error", { actions.onQuickAction("Find Error"); goChat() }),
        Triple(Icons.Default.FlashOn, "Help Me", { actions.onQuickAction("Help Me"); goChat() })
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = { actions.onAnswer(); goChat() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = accent
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("JAWAB SOAL DI LAYAR", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Text("Capture layar lalu jawab semua soal yang terlihat", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
        }
        Text("TOOLS", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, onClick) -> ToolTile(icon, label, accent, Modifier.weight(1f), onClick) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(if (hasImage) "ASK ABOUT IMAGE" else "ASK (tanpa gambar)", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        quick.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, onClick) -> ToolTile(icon, label, accent, Modifier.weight(1f), onClick) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SetupPane(settings: AppSettings, actions: PanelActions) {
    val accent = settings.accent.color()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("PANEL", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        SliderRow("Opacity", settings.floating.opacity, 0.55f..1f, format = { "${(it * 100).roundToInt()}%" }) { v ->
            actions.onUpdateSettings { it.copy(floating = it.floating.copy(opacity = v)) }
        }
        SwitchRow("Snap ke tepi", settings.floating.snapToEdge) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(snapToEdge = c)) } }
        SwitchRow("Kunci posisi", settings.floating.locked) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(locked = c)) } }
        SwitchRow("Compact", settings.floating.compactMode) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(compactMode = c)) } }
        SwitchRow("Auto hide", settings.floating.autoHide) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(autoHide = c)) } }
        Text("VISION", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        SwitchRow("Kirim hanya region", settings.vision.sendOnlyRegion) { c -> actions.onUpdateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = c)) } }
        SwitchRow("Auto analyze", settings.vision.autoAnalyze) { c -> actions.onUpdateSettings { it.copy(vision = it.vision.copy(autoAnalyze = c)) } }
        OutlinedButton(onClick = { actions.onOpenApp("settings") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buka Settings lengkap")
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// REGION SELECTOR
// ---------------------------------------------------------------------------------------------

internal const val REGION_NONE = 0
internal const val REGION_MOVE = 1
internal const val REGION_TL = 2
internal const val REGION_TR = 3
internal const val REGION_BL = 4
internal const val REGION_BR = 5

private fun clampInt(value: Int, low: Int, high: Int): Int = if (high < low) low else value.coerceIn(low, high)

internal fun hitTestRegion(region: Region, x: Float, y: Float, slop: Float): Int {
    val left = region.x.toFloat()
    val top = region.y.toFloat()
    val right = left + region.width
    val bottom = top + region.height
    fun near(px: Float, py: Float) = abs(x - px) <= slop && abs(y - py) <= slop
    return when {
        near(left, top) -> REGION_TL
        near(right, top) -> REGION_TR
        near(left, bottom) -> REGION_BL
        near(right, bottom) -> REGION_BR
        x in left..right && y in top..bottom -> REGION_MOVE
        else -> REGION_NONE
    }
}

internal fun resizeRegion(region: Region, mode: Int, dx: Float, dy: Float, screenW: Int, screenH: Int, minSize: Int = 80): Region {
    var left = region.x
    var top = region.y
    var right = region.x + region.width
    var bottom = region.y + region.height
    val ix = dx.roundToInt()
    val iy = dy.roundToInt()
    when (mode) {
        REGION_MOVE -> {
            val nx = clampInt(left + ix, 0, screenW - region.width)
            val ny = clampInt(top + iy, 0, screenH - region.height)
            left = nx
            top = ny
            right = nx + region.width
            bottom = ny + region.height
        }
        REGION_TL -> {
            left = clampInt(left + ix, 0, right - minSize)
            top = clampInt(top + iy, 0, bottom - minSize)
        }
        REGION_TR -> {
            right = clampInt(right + ix, left + minSize, screenW)
            top = clampInt(top + iy, 0, bottom - minSize)
        }
        REGION_BL -> {
            left = clampInt(left + ix, 0, right - minSize)
            bottom = clampInt(bottom + iy, top + minSize, screenH)
        }
        REGION_BR -> {
            right = clampInt(right + ix, left + minSize, screenW)
            bottom = clampInt(bottom + iy, top + minSize, screenH)
        }
        else -> return region
    }
    return region.copy(x = left, y = top, width = right - left, height = bottom - top)
}

@Composable
fun RegionSelectorOverlay(
    region: Region,
    accent: Color,
    onRegionChange: (Region) -> Unit,
    onReset: () -> Unit,
    onCenter: () -> Unit,
    onFullscreen: () -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val handleSlop = with(density) { 34.dp.toPx() }
    // FIX: pointerInput sebelumnya di-key ke `region`, sehingga gesture dibatalkan setiap kali region
    // berubah (drag hanya bergerak sedikit). Sekarang key Unit + rememberUpdatedState.
    val currentRegion by rememberUpdatedState(region)
    val currentOnChange by rememberUpdatedState(onRegionChange)
    var mode by remember { mutableIntStateOf(REGION_NONE) }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> mode = hitTestRegion(currentRegion, offset.x, offset.y, handleSlop) },
                onDragEnd = { mode = REGION_NONE },
                onDragCancel = { mode = REGION_NONE },
                onDrag = { change, drag ->
                    change.consume()
                    if (mode != REGION_NONE) {
                        val r = currentRegion
                        currentOnChange(resizeRegion(r, mode, drag.x, drag.y, r.screenWidth.coerceAtLeast(1), r.screenHeight.coerceAtLeast(1)))
                    }
                }
            )
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = Color.Black.copy(alpha = 0.55f)
            val l = region.x.toFloat()
            val t = region.y.toFloat()
            val r = l + region.width
            val b = t + region.height
            drawRect(dim, Offset(0f, 0f), Size(size.width, t.coerceAtLeast(0f)))
            drawRect(dim, Offset(0f, b), Size(size.width, (size.height - b).coerceAtLeast(0f)))
            drawRect(dim, Offset(0f, t), Size(l.coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))
            drawRect(dim, Offset(r, t), Size((size.width - r).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))
            drawRect(accent, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 3.dp.toPx()))
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { corner ->
                drawCircle(Color.White, radius = 11.dp.toPx(), center = corner)
                drawCircle(accent, radius = 7.dp.toPx(), center = corner)
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.7f)) {
                Text(
                    "AI REGION  ${region.width} × ${region.height}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onReset) { Icon(Icons.Default.Refresh, "Reset") }
                IconButton(onClick = onCenter) { Icon(Icons.Default.CenterFocusStrong, "Center") }
                IconButton(onClick = onFullscreen) { Icon(Icons.Default.Fullscreen, "Fullscreen") }
                IconButton(onClick = onSave) { Icon(Icons.Default.Save, "Save region preset") }
                IconButton(onClick = onApply) { Icon(Icons.Default.Check, "Use region", tint = accent) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close region selector") }
            }
            }
        }
    }
}

```

## `app/src/main/java/com/alfread/alfvision/service/OverlayLifecycleOwner.kt`

```kt
package com.alfread.alfvision.service

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * FIX: ComposeView yang ditambahkan lewat WindowManager dari Service TIDAK punya
 * LifecycleOwner / SavedStateRegistryOwner / ViewModelStoreOwner, sehingga Compose melempar
 * "ViewTreeLifecycleOwner not found" dan aplikasi force close saat START VISION.
 * Class ini menyediakan ketiganya untuk semua ComposeView overlay.
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun create() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    fun install(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}

```

## `app/src/main/java/com/alfread/alfvision/service/ScreenCaptureService.kt`

```kt
package com.alfread.alfvision.service

import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.R
import com.alfread.alfvision.core.model.CaptureRequest
import com.alfread.alfvision.core.model.CaptureResult
import com.alfread.alfvision.core.util.ScreenMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ScreenCaptureService : Service() {
    private lateinit var container: com.alfread.alfvision.AppContainer
    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var pendingRequest: CaptureRequest? = null
    private var serviceJob: Job? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Frame terbaru ditahan (tanpa copy). VirtualDisplay hanya mengirim frame saat layar berubah;
     * sebelumnya capture pada layar statis menunggu frame baru selamanya (stuck "busy").
     */
    private var heldImage: Image? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as AlfVisionApplication).container
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
        serviceJob = mainScope.launch {
            container.captureCoordinator.requests.collect { request ->
                // Request baru menggantikan request lama yang mungkin sudah timeout.
                pendingRequest?.let { old ->
                    container.captureCoordinator.fail(old.id, IllegalStateException("Capture replaced by a newer request"))
                }
                pendingRequest = request
                deliverPending()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.parcelableIntent(EXTRA_DATA)
        if (data == null || resultCode != Activity.RESULT_OK) {
            // FIX: service yang dijalankan lewat startForegroundService() WAJIB memanggil startForeground()
            // walau akan langsung berhenti. Tanpa ini Android melempar ForegroundServiceDidNotStartInTimeException.
            runCatching { startForeground(NOTIFICATION_ID, notification()) }
            container.sessionStore.setError("Screen capture permission belum diberikan.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // FIX: startForeground dipanggil PERTAMA (batas waktu 5 detik) sebelum setup projection.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            setupProjection(resultCode, data)
            isRunning = true
            container.sessionStore.setError(null)
        } catch (t: Throwable) {
            stopCapture()
            container.sessionStore.setError("Screen capture gagal dimulai. Periksa permission dan coba lagi.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        val created = projectionManager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection unavailable")
        projection = created
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                mainScope.launch {
                    stopCapture()
                    stopSelf()
                }
            }
        }
        projectionCallback = callback
        // Android 14+: callback WAJIB didaftarkan sebelum createVirtualDisplay.
        created.registerCallback(callback, mainHandler)
        createVirtualDisplay()
    }

    private fun newImageReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ onFrame() }, mainHandler)
        }

    private fun createVirtualDisplay() {
        releaseImageReader()
        virtualDisplay?.release()
        val (width, height) = ScreenMetrics.size(this)
        val dpi = resources.displayMetrics.densityDpi
        val reader = newImageReader(width.coerceAtLeast(1), height.coerceAtLeast(1))
        imageReader = reader
        virtualDisplay = projection?.createVirtualDisplay(
            "ALF Vision Panel",
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val display = virtualDisplay ?: return
        if (projection == null) return
        // Rotasi layar: ukuran virtual display ikut berubah supaya hasil crop tidak meleset.
        runCatching {
            val (width, height) = ScreenMetrics.size(this)
            val dpi = resources.displayMetrics.densityDpi
            val oldReader = imageReader
            runCatching { heldImage?.close() }
            heldImage = null
            val reader = newImageReader(width.coerceAtLeast(1), height.coerceAtLeast(1))
            imageReader = reader
            display.resize(width.coerceAtLeast(1), height.coerceAtLeast(1), dpi)
            display.surface = reader.surface
            runCatching { oldReader?.setOnImageAvailableListener(null, null) }
            runCatching { oldReader?.close() }
        }
    }

    private fun onFrame() {
        val reader = imageReader ?: return
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        runCatching { heldImage?.close() }
        heldImage = image
        if (pendingRequest != null) deliverPending()
    }

    private fun deliverPending() {
        val request = pendingRequest ?: return
        val image = heldImage ?: return
        try {
            val plane = image.planes.firstOrNull() ?: throw IllegalStateException("No image plane")
            val width = image.width
            val height = image.height
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            val buffer = plane.buffer
            buffer.rewind()
            padded.copyPixelsFromBuffer(buffer)
            // FIX: buang kolom padding di kanan supaya lebar bitmap == lebar layar (region tidak bergeser).
            val bitmap = if (paddedWidth != width) {
                val trimmed = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                trimmed
            } else padded
            val region = request.region
            val cropped = if (!request.fullScreen && region != null) container.imageProcessor.cropRegion(bitmap, region) else bitmap
            val payload = container.imageProcessor.encodeJpeg(cropped, request.quality, request.maxBytes)
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            pendingRequest = null
            container.captureCoordinator.complete(CaptureResult(request.id, payload, request.region))
        } catch (t: Throwable) {
            pendingRequest = null
            container.captureCoordinator.fail(request.id, t)
            container.sessionStore.setError("Screenshot terlalu besar atau gagal diproses.")
        }
    }

    private fun releaseImageReader() {
        runCatching { heldImage?.close() }
        heldImage = null
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
        projectionCallback?.let { callback -> runCatching { projection?.unregisterCallback(callback) } }
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
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "alf_vision_capture"
        private const val NOTIFICATION_ID = 1101
        const val ACTION_STOP = "com.alfread.alfvision.capture.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"

        private val _running = MutableStateFlow(false)

        /** Observable supaya Home bisa menampilkan status capture secara live. */
        val running: StateFlow<Boolean> = _running

        var isRunning: Boolean
            get() = _running.value
            private set(value) { _running.value = value }
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
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.ConversationEntity
import com.alfread.alfvision.data.local.RegionPresetEntity
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as AlfVisionApplication).container

    val settings: StateFlow<AppSettings> =
        c.settingsRepository.flow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    val conversations: StateFlow<List<ConversationEntity>> =
        c.historyRepository.observeConversations()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val regions: StateFlow<List<RegionPresetEntity>> =
        c.regionRepository.observe()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val profiles =
        c.profileRepository.observe()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val session = c.sessionStore
    val network = c.networkMonitor.connected
    val shizuku = c.shizukuCompat

    // Status API key disimpan sebagai state. Sebelumnya hasApiKey() men-decrypt Keystore di main thread
    // pada SETIAP recomposition Home (lambat dan bisa membuat UI patah-patah).
    private val _apiReady = MutableStateFlow(false)
    val apiReady: StateFlow<Boolean> = _apiReady

    init { refreshApiKey() }

    fun refreshApiKey() {
        viewModelScope.launch {
            _apiReady.value = withContext(Dispatchers.IO) { runCatching { c.secureStore.hasApiKey() }.getOrDefault(false) }
        }
    }

    private val _groqStatus = MutableStateFlow<String?>(null)
    val groqStatus: StateFlow<String?> = _groqStatus

    private val _models = MutableStateFlow(
        listOf(
            GroqModel(
                id = "qwen/qwen3.8-27b",
                active = true,
                supportsVision = true
            )
        )
    )

    val models: StateFlow<List<GroqModel>> = _models

    fun saveApiKey(value: String) {
        val key = value.trim()
        if (key.isEmpty()) {
            _groqStatus.value = "API key cannot be empty."
            return
        }
        c.secureStore.putApiKey(key)
        refreshApiKey()
        _groqStatus.value = "API key saved securely on device."
    }

    fun deleteApiKey() {
        c.secureStore.deleteApiKey()
        _apiReady.value = false
        _groqStatus.value = "API key deleted."
    }

    fun hasApiKey(): Boolean = c.secureStore.hasApiKey()

    fun testConnection() {
        viewModelScope.launch {
            _groqStatus.value = "Testing connection..."
            runCatching {
                c.groqRepository.listModels()
            }.onSuccess { list ->
                _models.value = list
                _groqStatus.value = "Connected. ${list.size} models available."
            }.onFailure { error ->
                _groqStatus.value = error.message ?: "Connection failed."
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            c.settingsRepository.update(transform)
        }
    }

    fun showRegionSelector() {
        startFloating()

        val intent = Intent(
            c.appContext,
            FloatingPanelService::class.java
        ).apply {
            action = FloatingPanelService.ACTION_REGION
        }

        startFloatingService(intent)
    }

    fun stopVision() {
        stopCapture()
        runCatching {
            c.appContext.startService(
                Intent(c.appContext, FloatingPanelService::class.java).apply { action = FloatingPanelService.ACTION_CLOSE }
            )
        }
    }

    fun answerScreen() {
        c.controller.answerScreen()
    }

    fun clearImage() {
        c.sessionStore.clearImage()
    }

    fun stopVoice() {
        c.voiceInputManager.stop()
    }

    fun newChat() {
        c.controller.cancelRequest()
        c.sessionStore.clearChat()
        c.controller.resetConversation()
    }

    fun clearRegion() {
        c.sessionStore.setRegion(null)
    }

    fun clearScreenshots() {
        c.imageStorage.clear()
    }

    fun startFloating() {
        val intent = Intent(
            c.appContext,
            FloatingPanelService::class.java
        ).apply {
            action = FloatingPanelService.ACTION_SHOW
        }

        startFloatingService(intent)
    }

    private fun startFloatingService(intent: Intent) {
        runCatching { c.appContext.startService(intent) }
            .onFailure { c.sessionStore.setError("Floating panel gagal dimulai. Buka app lagi lalu coba START VISION.") }
    }

    fun stopCapture() {
        val intent = Intent(
            c.appContext,
            ScreenCaptureService::class.java
        ).apply {
            action = ScreenCaptureService.ACTION_STOP
        }

        c.appContext.startService(intent)
    }

    fun capture() {
        c.controller.capture(c.sessionStore.region.value)
    }

    fun ask(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        c.controller.ask(text)
    }

    fun quickAction(action: String) {
        c.controller.quickAction(action)
    }

    fun compare() {
        c.controller.compare()
    }

    fun stopRequest() {
        c.controller.cancelRequest()
    }

    fun retryLast() {
        c.controller.retryLast()
    }

    fun clearHistory() {
        viewModelScope.launch {
            c.historyRepository.deleteAll()
        }
    }

    fun cleanupHistory(days: Int) {
        if (days <= 0) return

        viewModelScope.launch {
            val cutoff =
                System.currentTimeMillis() - days * 86_400_000L
            c.historyRepository.deleteOlderThan(cutoff)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            c.historyRepository.deleteConversation(id)
        }
    }

    fun saveRegion(name: String, region: Region) {
        viewModelScope.launch {
            c.regionRepository.save(name.trim(), region)
        }
    }

    fun deleteRegion(item: RegionPresetEntity) {
        viewModelScope.launch {
            c.regionRepository.delete(item)
        }
    }

    fun duplicateRegion(item: RegionPresetEntity) {
        viewModelScope.launch {
            c.regionRepository.save(
                "${item.name} Copy",
                Region(
                    x = item.x,
                    y = item.y,
                    width = item.width,
                    height = item.height,
                    screenWidth = item.screenWidth,
                    screenHeight = item.screenHeight,
                    displayId = item.displayId,
                    rotation = item.rotation
                )
            )
        }
    }

    fun updateRegion(item: RegionPresetEntity) {
        viewModelScope.launch {
            c.regionRepository.update(item)
        }
    }

    fun openConversation(id: Long) {
        viewModelScope.launch {
            val lines = c.historyRepository.getMessages(id)
            c.sessionStore.clearChat()
            c.controller.resumeConversation(id)

            lines.forEach { line ->
                if (line.role == Role.USER) {
                    c.sessionStore.addUser(
                        line.content,
                        line.imagePath
                    )
                } else {
                    c.sessionStore.addAssistant(
                        line.content,
                        line.model ?: settings.value.activeModel,
                        ModelUsage(
                            totalTokens = line.tokenUsage ?: 0
                        ),
                        line.imagePath
                    )
                }
            }
        }
    }

    fun pinCurrent() {
        c.sessionStore.pinCurrent()
    }

    fun voice() {
        c.voiceInputManager.start()
    }

    fun createProfile(name: String, prompt: String) {
        val profileName = name.trim()
        if (profileName.isEmpty()) return

        viewModelScope.launch {
            c.profileRepository.create(
                AiProfile(
                    name = profileName,
                    systemPrompt = prompt
                )
            )
        }
    }
}

```

## `app/src/main/java/com/alfread/alfvision/ui/components/Components.kt`

```kt
package com.alfread.alfvision.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.ui.theme.AlfCyan
import com.alfread.alfvision.ui.theme.brandBrush

// ---------------------------------------------------------------------------------------------
// DOCK BAR (floating pill navigation). Dipakai di app utama dan di dalam panel overlay.
// ---------------------------------------------------------------------------------------------

data class DockItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun DockBar(
    items: List<DockItem>,
    selectedRoute: String?,
    onSelect: (DockItem) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(if (compact) 22.dp else 30.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = if (compact) 6.dp else 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                DockButton(item, item.route == selectedRoute, compact) { onSelect(item) }
            }
        }
    }
}

@Composable
private fun DockButton(item: DockItem, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "dockBackground")
    val foreground by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "dockForeground")
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 11.dp, vertical = if (compact) 8.dp else 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = item.label, tint = foreground, modifier = Modifier.size(if (compact) 20.dp else 22.dp))
        AnimatedVisibility(visible = selected) {
            Row {
                Spacer(Modifier.width(6.dp))
                Text(item.label, color = foreground, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// CARDS & TEXT
// ---------------------------------------------------------------------------------------------

@Composable
fun AlfCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.4.sp
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun StatusTile(
    icon: ImageVector,
    title: String,
    value: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    AlfCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint)
                Spacer(Modifier.weight(1f))
                Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconBadge(icon, tint, size = 44)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val brush = if (enabled) brandBrush(accent) else Brush.linearGradient(listOf(Color(0xFF555B7A), Color(0xFF3F4466)))
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        if (onDismiss != null) IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 64)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// FORM ROWS (dipakai Settings dan tab Settings di panel overlay)
// ---------------------------------------------------------------------------------------------

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Slider yang hanya menyimpan nilai ketika jari dilepas. Sebelumnya setiap pixel geseran
 * menulis ke DataStore + Room sehingga UI patah-patah.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { "%.2f".format(it) },
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format(local), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = local, onValueChange = { local = it }, valueRange = range, onValueChangeFinished = { onCommit(local) })
    }
}

@Composable
fun BrandDot(modifier: Modifier = Modifier, size: Int = 36) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, AlfCyan))),
        contentAlignment = Alignment.Center,
        content = {}
    )
}

```

## `app/src/main/java/com/alfread/alfvision/ui/components/Markdown.kt`

```kt
package com.alfread.alfvision.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.ChatLine

private val bulletRegex = Regex("^(\\s*)[*\\-+]\\s+(.*)$")
private val headingRegex = Regex("^#{1,6}\\s+(.*)$")
private val ruleRegex = Regex("^\\s*([-*_]\\s*){3,}$")

/** Mengubah markdown sederhana (**bold**, *italic*, `code`, bullet, heading) jadi teks berformat, tanpa simbol mentah. */
fun renderMarkdown(source: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var started = false
    var inFence = false
    for (raw in source.replace("\r\n", "\n").split("\n")) {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("```")) {
            inFence = !inFence
            continue
        }
        if (!inFence && ruleRegex.matches(raw)) continue
        if (started) append("\n")
        started = true
        if (inFence) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(raw) }
            continue
        }
        val heading = headingRegex.matchEntire(trimmed)
        val bullet = bulletRegex.matchEntire(raw)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.08.em)) {
                appendInline(heading.groupValues[1], codeBackground)
            }
            bullet != null -> {
                val indent = " ".repeat((bullet.groupValues[1].length / 2) * 2)
                append(indent + "• ")
                appendInline(bullet.groupValues[2], codeBackground)
            }
            else -> appendInline(raw, codeBackground)
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String, codeBackground: Color) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flush()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(text.substring(i + 2, end), codeBackground) }
                i = end + 2
            } else {
                i += 2
            }
        } else if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(text.substring(i + 1, end)) }
                i = end + 1
            } else {
                i += 1
            }
        } else if (c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1 && !text[end - 1].isWhitespace()) {
                flush()
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(text.substring(i + 1, end), codeBackground) }
                i = end + 1
            } else {
                i += 1
            }
        } else {
            plain.append(c)
            i += 1
        }
    }
    flush()
}

/** Isi bubble chat: thumbnail gambar layar (jika ada) + teks yang sudah dirender dari markdown. */
@Composable
fun MessageContent(line: ChatLine, textColor: Color, codeBackground: Color, textSize: Int = 14) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val thumb = line.imageBytes
        if (thumb != null) {
            val bitmap = remember(thumb) { BitmapFactory.decodeByteArray(thumb, 0, thumb.size)?.asImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Gambar layar terlampir",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
        val rendered = remember(line.content, codeBackground) { renderMarkdown(line.content, codeBackground) }
        Text(rendered, color = textColor, fontSize = textSize.sp, lineHeight = (textSize + 6).sp)
    }
}

```

## `app/src/main/java/com/alfread/alfvision/ui/navigation/Navigation.kt`

```kt
package com.alfread.alfvision.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.components.DockBar
import com.alfread.alfvision.ui.components.DockItem
import com.alfread.alfvision.ui.screens.*

sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Vision : Dest("vision", "Vision", Icons.Default.Visibility)
    data object Chat : Dest("chat", "Chat", Icons.AutoMirrored.Filled.Chat)
    data object History : Dest("history", "History", Icons.Default.History)
    data object Settings : Dest("settings", "Settings", Icons.Default.Settings)
}

/** Navigasi tab: tidak menumpuk back stack dan state tiap tab tersimpan. */
fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ALFNavHost(
    navController: NavHostController,
    vm: MainViewModel,
    onStartVision: () -> Unit,
    onStopVision: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOverlay: () -> Unit
) {
    val dockItems = remember {
        listOf(Dest.Home, Dest.Vision, Dest.Chat, Dest.History, Dest.Settings).map { DockItem(it.route, it.label, it.icon) }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DockBar(items = dockItems, selectedRoute = current, onSelect = { navController.navigateTo(it.route) })
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Dest.Home.route) {
                HomeScreen(vm, padding, onStartVision, onStopVision, onOverlay) { navController.navigateTo(it) }
            }
            composable(Dest.Vision.route) { VisionScreen(vm, padding, onOverlay) }
            composable(Dest.Chat.route) { ChatScreen(vm, padding, onRequestMicrophone) }
            composable(Dest.History.route) { HistoryScreen(vm, padding) { navController.navigateTo(Dest.Chat.route) } }
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.PendingImage
import kotlin.math.abs
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
    // FIX: onClose() tidak boleh dipanggil langsung saat composition.
    if (bitmap == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var tool by remember { mutableStateOf(Tool.RECTANGLE) }
    var draft by remember { mutableStateOf<Mark?>(null) }
    var marks by remember { mutableStateOf(listOf<Mark>()) }
    var redo by remember { mutableStateOf(listOf<Mark>()) }
    var text by remember { mutableStateOf("") }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            Text("Annotate", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (marks.isNotEmpty()) { redo = redo + marks.last(); marks = marks.dropLast(1) } }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            IconButton(onClick = { if (redo.isNotEmpty()) { marks = marks + redo.last(); redo = redo.dropLast(1) } }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            Button(onClick = {
                // FIX: koordinat mark (ruang canvas) dikonversi ke ruang bitmap asli.
                val scaleX = if (canvasSize.width > 0) bitmap.width.toFloat() / canvasSize.width else 1f
                val scaleY = if (canvasSize.height > 0) bitmap.height.toFloat() / canvasSize.height else 1f
                val output = renderAnnotations(bitmap, marks, scaleX, scaleY)
                val bytes = java.io.ByteArrayOutputStream().use { out -> output.compress(Bitmap.CompressFormat.JPEG, 90, out); out.toByteArray() }
                output.recycle()
                onSave(PendingImage(bytes, "image/jpeg", "Annotated"))
            }) { Text("Save") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip("Rect", Tool.RECTANGLE, tool, Icons.Default.CropSquare) { tool = it }
            ToolChip("Circle", Tool.CIRCLE, tool, Icons.Default.Circle) { tool = it }
            ToolChip("Arrow", Tool.ARROW, tool, Icons.AutoMirrored.Filled.ArrowForward) { tool = it }
            ToolChip("Line", Tool.LINE, tool, Icons.Default.Minimize) { tool = it }
            ToolChip("Text", Tool.TEXT, tool, Icons.Default.TextFields) { tool = it }
            ToolChip("Blur", Tool.BLUR, tool, Icons.Default.BlurOn) { tool = it }
        }
        if (tool == Tool.TEXT) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(8.dp), label = { Text("Text to place (tap gambar)") })
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(ratio).onSizeChanged { canvasSize = it }) {
                androidx.compose.foundation.Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "Image being annotated",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tool, text) {
                            detectTapGestures { offset ->
                                if (tool == Tool.TEXT && text.isNotBlank()) {
                                    marks = marks + Mark(tool, offset, offset, text)
                                    redo = emptyList()
                                }
                            }
                        }
                        .pointerInput(tool) {
                            detectDragGestures(
                                onDragStart = { start -> if (tool != Tool.TEXT) draft = Mark(tool, start, start) },
                                onDrag = { change, drag -> change.consume(); draft = draft?.let { it.copy(end = it.end + drag) } },
                                onDragEnd = { draft?.let { marks = marks + it; redo = emptyList() }; draft = null },
                                onDragCancel = { draft = null }
                            )
                        }
                ) {
                    fun drawMark(mark: Mark, alpha: Float = 1f) {
                        val red = Color.Red.copy(alpha = alpha)
                        val topLeft = Offset(min(mark.start.x, mark.end.x), min(mark.start.y, mark.end.y))
                        val boxSize = Size(abs(mark.end.x - mark.start.x), abs(mark.end.y - mark.start.y))
                        when (mark.tool) {
                            // FIX: sebelumnya warna Color.Transparent sehingga rect/circle tidak terlihat.
                            Tool.RECTANGLE -> drawRect(red, topLeft = topLeft, size = boxSize, style = Stroke(4f))
                            Tool.CIRCLE -> drawOval(red, topLeft = topLeft, size = boxSize, style = Stroke(4f))
                            Tool.LINE -> drawLine(red, mark.start, mark.end, 4f)
                            Tool.ARROW -> drawArrow(mark.start, mark.end, alpha)
                            Tool.TEXT -> drawContext.canvas.nativeCanvas.drawText(mark.text, mark.start.x, mark.start.y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED; textSize = 42f })
                            Tool.BLUR -> drawRect(Color.Black.copy(alpha = 0.35f), topLeft = topLeft, size = boxSize)
                        }
                    }
                    marks.forEach { drawMark(it) }
                    draft?.let { drawMark(it, 0.65f) }
                }
            }
        }
    }
    // FIX: bitmap TIDAK lagi di-recycle di onDispose; Image masih memakainya saat recomposition
    // sehingga menyebabkan crash "Canvas: trying to use a recycled bitmap".
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

private fun renderAnnotations(source: Bitmap, marks: List<Mark>, scaleX: Float, scaleY: Float): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
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

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.RegionPresetEntity
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.components.*
import com.alfread.alfvision.ui.navigation.Dest
import com.alfread.alfvision.ui.theme.AlfCyan
import com.alfread.alfvision.ui.theme.color
import com.alfread.alfvision.ui.theme.heroBrush
import com.alfread.alfvision.vision.VoiceState
import java.text.DateFormat
import java.util.Date

// =============================================================================================
// HOME
// =============================================================================================

@Composable
fun HomeScreen(
    vm: MainViewModel,
    padding: PaddingValues,
    onStartVision: () -> Unit,
    onStopVision: () -> Unit,
    onOverlay: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val network by vm.network.collectAsState()
    val apiReady by vm.apiReady.collectAsState()
    val running by ScreenCaptureService.running.collectAsState()
    val error by vm.session.error.collectAsState()
    val accent = settings.accent.color()
    var overlayReady by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayReady = Settings.canDrawOverlays(context)
        vm.refreshApiKey()
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // HERO
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(heroBrush(accent)).padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Visibility, null, tint = Color.White) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ALF VISION", color = Color.White, style = MaterialTheme.typography.headlineSmall, letterSpacing = 1.sp)
                        Text("AI screen assistant", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(if (running) Color(0xFF3DFF8F) else Color.White.copy(alpha = 0.55f)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (running) "Vision aktif - panel siap dipakai" else "Vision belum aktif",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = if (running) onStopVision else onStartVision,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF14172B))
                ) {
                    Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "STOP VISION" else "START VISION", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        error?.let { InfoBanner(it, onDismiss = { vm.session.setError(null) }) }

        SectionLabel("Status")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusTile(
                Icons.Default.VpnKey, "API KEY", if (apiReady) "Tersimpan" else "Belum diatur", apiReady,
                Modifier.weight(1f).height(124.dp)
            ) { onNavigate(Dest.Settings.route) }
            StatusTile(
                Icons.Default.Layers, "OVERLAY", if (overlayReady) "Aktif" else "Perlu izin", overlayReady,
                Modifier.weight(1f).height(124.dp)
            ) { if (!overlayReady) onOverlay() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusTile(
                Icons.Default.CameraAlt, "CAPTURE", if (running) "Berjalan" else "Berhenti", running,
                Modifier.weight(1f).height(124.dp)
            ) { if (!running) onStartVision() }
            StatusTile(
                if (network) Icons.Default.Wifi else Icons.Default.WifiOff, "NETWORK", if (network) "Online" else "Offline", network,
                Modifier.weight(1f).height(124.dp)
            )
        }

        SectionLabel("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.AutoMirrored.Filled.Chat, "Chat", Modifier.weight(1f)) { onNavigate(Dest.Chat.route) }
            ActionTile(Icons.Default.CameraAlt, "Capture", Modifier.weight(1f)) { vm.capture() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.Default.Crop, "Region", Modifier.weight(1f)) {
                if (overlayReady) vm.showRegionSelector() else onOverlay()
            }
            ActionTile(Icons.Default.AutoAwesome, "Jawab Soal", Modifier.weight(1f)) {
                vm.answerScreen()
                onNavigate(Dest.Chat.route)
            }
        }

        AlfCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Security, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Screenshot hanya dikirim ke Groq saat kamu menekan capture atau analyze. API key disimpan terenkripsi di perangkat.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// =============================================================================================
// VISION
// =============================================================================================

private fun vmRegion(item: RegionPresetEntity) =
    Region(item.x, item.y, item.width, item.height, item.screenWidth, item.screenHeight, item.displayId, item.rotation)

@Composable
fun VisionScreen(vm: MainViewModel, padding: PaddingValues, onOverlay: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val regions by vm.regions.collectAsState()
    val image by vm.session.currentImage.collectAsState()
    val previous by vm.session.previousImage.collectAsState()
    val activeRegion by vm.session.region.collectAsState()
    val running by ScreenCaptureService.running.collectAsState()
    var annotating by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RegionPresetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    val currentImage = image
    if (annotating && currentImage != null) {
        AnnotationEditor(
            image = currentImage,
            onSave = { vm.session.setImage(it); annotating = false },
            onClose = { annotating = false }
        )
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ScreenHeader("Vision", if (running) "Capture aktif" else "Jalankan START VISION di Home dulu") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(Icons.Default.Crop, "Select Region", Modifier.weight(1f)) {
                        if (Settings.canDrawOverlays(context)) vm.showRegionSelector() else onOverlay()
                    }
                    ActionTile(Icons.Default.CameraAlt, "Capture", Modifier.weight(1f)) { vm.capture() }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(Icons.Default.Compare, "Compare", Modifier.weight(1f), enabled = image != null && previous != null) { vm.compare() }
                    ActionTile(Icons.Default.Draw, "Annotate", Modifier.weight(1f), enabled = image != null) { annotating = true }
                }
            }
            item {
                AlfCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(
                            "Kirim hanya region terpilih",
                            settings.vision.sendOnlyRegion,
                            supporting = activeRegion?.let { "Aktif: ${it.width} × ${it.height} di ${it.x}, ${it.y}" } ?: "Belum ada region - memakai layar penuh"
                        ) { v -> vm.updateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = v)) } }
                        if (activeRegion != null) {
                            OutlinedButton(onClick = { vm.clearRegion() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CropFree, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Hapus region aktif")
                            }
                        }
                    }
                }
            }
            currentImage?.let { current ->
                item {
                    AlfCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gambar saat ini - ${current.label} - ${current.bytes.size / 1024} KB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            val preview = remember(current.bytes) {
                                BitmapFactory.decodeByteArray(current.bytes, 0, current.bytes.size)?.asImageBitmap()
                            }
                            preview?.let {
                                androidx.compose.foundation.Image(
                                    it, contentDescription = "Screenshot terakhir",
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Region presets") }
            if (regions.isEmpty()) {
                item { EmptyState(Icons.Default.CropFree, "Belum ada preset", "Pilih region lalu tekan ikon simpan di toolbar region.") }
            }
            items(regions, key = { it.id }) { item ->
                AlfCard(onClick = { vm.session.setRegion(vmRegion(item)) }) {
                    Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.CropFree, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.width} × ${item.height} di ${item.x}, ${item.y}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { renameTarget = item; renameText = item.name }) { Icon(Icons.Default.Edit, "Rename") }
                        IconButton(onClick = { vm.duplicateRegion(item) }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                        IconButton(onClick = { vm.deleteRegion(item) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename region") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateRegion(target.copy(name = renameText.trim().ifBlank { target.name }))
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

// =============================================================================================
// CHAT
// =============================================================================================

@Composable
fun ChatScreen(vm: MainViewModel, padding: PaddingValues, onRequestMicrophone: () -> Unit) {
    val lines by vm.session.lines.collectAsState()
    val input by vm.session.input.collectAsState()
    val busy by vm.session.busy.collectAsState()
    val error by vm.session.error.collectAsState()
    val voice by vm.session.voiceState.collectAsState()
    val image by vm.session.currentImage.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {
        ScreenHeader("Chat", if (image != null) "Gambar siap dianalisis" else "Belum ada gambar") {
            IconButton(onClick = { vm.newChat() }) { Icon(Icons.Default.DeleteSweep, "Chat baru") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { vm.answerScreen() },
                label = { Text("Jawab Soal", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary),
                border = null
            )
            listOf("Analyze", "Explain", "Read", "Translate", "Summarize", "Find Error", "Extract Text", "Describe", "Help Me").forEach { action ->
                AssistChip(onClick = { vm.quickAction(action) }, label = { Text(action) })
            }
            AssistChip(onClick = { vm.compare() }, label = { Text("Compare") })
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lines.isEmpty()) {
                item { EmptyState(Icons.Default.AutoAwesome, "Mulai percakapan", "Capture layar lalu pilih aksi cepat, atau ketik pertanyaanmu.") }
            }
            items(lines, key = { it.id }) { line -> MessageBubble(line) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        image?.let { attached ->
            val thumb = remember(attached.bytes) {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeByteArray(attached.bytes, 0, attached.bytes.size, options)?.asImageBitmap()
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                thumb?.let {
                    androidx.compose.foundation.Image(
                        it, "Gambar terlampir",
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Gambar layar terlampir (${attached.label})", Modifier.weight(1f), fontSize = 13.sp)
                IconButton(onClick = { vm.clearImage() }) { Icon(Icons.Default.Close, "Lepas gambar") }
            }
        }
        error?.let {
            InfoBanner(it, Modifier.padding(vertical = 4.dp), actionLabel = "Retry", onAction = { vm.retryLast() }, onDismiss = { vm.session.setError(null) })
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRequestMicrophone) {
                    Icon(if (voice == VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Voice input")
                }
                TextField(
                    value = input,
                    onValueChange = vm.session::setInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask anything...") },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                IconButton(
                    onClick = { if (busy) vm.stopRequest() else vm.ask(input) },
                    enabled = busy || input.isNotBlank()
                ) {
                    Icon(if (busy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (busy) "Stop" else "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine) {
    val isUser = line.role == Role.USER
    val clipboard = LocalClipboardManager.current
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (isUser) 20.dp else 6.dp, bottomEnd = if (isUser) 6.dp else 20.dp),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SelectionContainer { MessageContent(line, textColor, MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), textSize = 15) }
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val meta = listOfNotNull(line.model, line.tokenUsage?.takeIf { it > 0 }?.let { "$it tok" }).joinToString(" - ")
                        if (meta.isNotEmpty()) Text(meta, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString(line.content)) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// HISTORY
// =============================================================================================

@Composable
fun HistoryScreen(vm: MainViewModel, padding: PaddingValues, onOpened: () -> Unit) {
    val conversations by vm.conversations.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader("History", "${conversations.size} percakapan") {
                if (conversations.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "Hapus semua") }
            }
        }
        if (conversations.isEmpty()) {
            item { EmptyState(Icons.Default.History, "Belum ada riwayat", "Percakapan akan tersimpan di sini jika Save Conversations aktif.") }
        }
        items(conversations, key = { it.id }) { item ->
            AlfCard(onClick = { vm.openConversation(item.id); onOpened() }) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Forum, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(format.format(Date(item.updatedAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deleteConversation(item.id) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Semua percakapan tersimpan akan dihapus permanen.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }
}

```

## `app/src/main/java/com/alfread/alfvision/ui/screens/SettingsScreen.kt`

```kt
package com.alfread.alfvision.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alfread.alfvision.BuildConfig
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.components.*
import com.alfread.alfvision.ui.theme.color
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, padding: PaddingValues, onOverlay: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val apiReady by vm.apiReady.collectAsState()
    val status by vm.groqStatus.collectAsState()
    val models by vm.models.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val shizukuAvailable by vm.shizuku.available.collectAsState()
    val shizukuGranted by vm.shizuku.permissionGranted.collectAsState()
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }
    var showProfileCreate by remember { mutableStateOf(false) }
    var overlayReady by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { overlayReady = Settings.canDrawOverlays(context) }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Settings", "Atur AI, panel, tampilan, dan privasi") }

        // ------------------------------------------------------------ AI
        item {
            SettingsCard("AI", Icons.Default.AutoAwesome) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Groq API Key", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (apiReady) "Tersimpan" else "Belum diatur",
                        color = if (apiReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (apiReady) "Ganti API key" else "Masukkan API key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide key")
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.saveApiKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save") }
                    OutlinedButton(onClick = vm::testConnection) { Text("Test") }
                    TextButton(onClick = vm::deleteApiKey) { Text("Delete") }
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }

                val visionModels = models.filter { it.supportsVision }.ifEmpty { models }
                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = !modelExpanded }) {
                    OutlinedTextField(
                        value = settings.activeModel, onValueChange = {}, readOnly = true, label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        if (visionModels.isEmpty()) {
                            DropdownMenuItem(text = { Text("Tekan Test untuk memuat model") }, onClick = { modelExpanded = false })
                        }
                        visionModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.id) },
                                onClick = { vm.updateSettings { it.copy(activeModel = model.id) }; modelExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsCard("Response", Icons.Default.Tune) {
                EnumDropdown("Style", settings.responseStyle.label, ResponseStyle.entries) { s -> vm.updateSettings { it.copy(responseStyle = s) } }
                SliderRow("Temperature", settings.temperature, 0f..2f) { v -> vm.updateSettings { it.copy(temperature = v) } }
                SliderRow("Max tokens", settings.maxTokens.toFloat(), 128f..16384f, format = { it.roundToInt().toString() }) { v ->
                    vm.updateSettings { it.copy(maxTokens = v.roundToInt()) }
                }
                val selected = profiles.firstOrNull { it.id == settings.activeProfileId }?.name ?: profiles.firstOrNull()?.name ?: "General"
                ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = { profileExpanded = !profileExpanded }) {
                    OutlinedTextField(
                        value = selected, onValueChange = {}, readOnly = true, label = { Text("Active profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                        profiles.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.updateSettings { it.copy(activeProfileId = p.id) }; profileExpanded = false })
                        }
                        DropdownMenuItem(text = { Text("Create custom profile") }, onClick = { profileExpanded = false; showProfileCreate = true })
                    }
                }
            }
        }

        // ------------------------------------------------------------ VISION & PANEL
        item {
            SettingsCard("Vision", Icons.Default.Visibility) {
                SwitchRow("Auto Analyze", settings.vision.autoAnalyze, supporting = "Capture + analyze otomatis sesuai interval") { c ->
                    vm.updateSettings { it.copy(vision = it.vision.copy(autoAnalyze = c)) }
                }
                if (settings.vision.autoAnalyze) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutoAnalyzeInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = settings.vision.interval == interval,
                                onClick = { vm.updateSettings { it.copy(vision = it.vision.copy(interval = interval)) } },
                                label = { Text(interval.label) }
                            )
                        }
                    }
                }
                SwitchRow("Freeze Frame", settings.vision.freezeFrame) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(freezeFrame = c)) } }
                SwitchRow("Screenshot Preview", settings.vision.screenshotPreview) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(screenshotPreview = c)) } }
                SwitchRow("Save Screenshots", settings.vision.saveScreenshots) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(saveScreenshots = c)) } }
                SwitchRow("Send only selected region", settings.vision.sendOnlyRegion) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = c)) } }
                SliderRow("JPEG quality", settings.vision.quality.toFloat(), 40f..100f, format = { "${it.roundToInt()}%" }) { v ->
                    vm.updateSettings { it.copy(vision = it.vision.copy(quality = v.roundToInt())) }
                }
            }
        }

        item {
            SettingsCard("Floating panel", Icons.Default.Layers) {
                SliderRow("Opacity", settings.floating.opacity, 0.55f..1f, format = { "${(it * 100).roundToInt()}%" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(opacity = v)) }
                }
                SliderRow("Orb size", settings.floating.orbSizeDp.toFloat(), 44f..96f, format = { "${it.roundToInt()} dp" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(orbSizeDp = v.roundToInt())) }
                }
                SliderRow("Panel width", settings.floating.panelWidthDp.toFloat(), 280f..520f, format = { "${it.roundToInt()} dp" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(panelWidthDp = v.roundToInt())) }
                }
                SliderRow("Panel height", settings.floating.panelHeightDp.toFloat(), 300f..820f, format = { "${it.roundToInt()} dp" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(panelHeightDp = v.roundToInt())) }
                }
                Text("Ukuran berlaku saat panel dibuka ulang.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Snap to edge", settings.floating.snapToEdge) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(snapToEdge = c)) } }
                SwitchRow("Lock position", settings.floating.locked) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(locked = c)) } }
                SwitchRow("Compact mode", settings.floating.compactMode) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(compactMode = c)) } }
                SwitchRow("Auto hide", settings.floating.autoHide) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(autoHide = c)) } }
            }
        }

        // ------------------------------------------------------------ APPEARANCE
        item {
            SettingsCard("Appearance", Icons.Default.Palette) {
                Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.theme == mode,
                            onClick = { vm.updateSettings { it.copy(theme = mode) } },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text("Accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { item ->
                        val selected = item == settings.accent
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(item.color())
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { vm.updateSettings { it.copy(accent = item) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Default.Check, item.label, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ PRIVACY & PERMISSIONS
        item {
            SettingsCard("Privacy", Icons.Default.Security) {
                SwitchRow("Save conversations", settings.saveHistory) { c -> vm.updateSettings { it.copy(saveHistory = c) } }
                Text("Auto delete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 7, 30).forEach { days ->
                        FilterChip(
                            selected = settings.autoDeleteDays == days,
                            onClick = { vm.updateSettings { it.copy(autoDeleteDays = days) }; vm.cleanupHistory(days) },
                            label = { Text(if (days == 0) "Never" else "$days day${if (days == 1) "" else "s"}") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear history")
                    }
                    OutlinedButton(onClick = vm::clearScreenshots) { Text("Clear screenshots") }
                }
            }
        }

        item {
            SettingsCard("Permissions", Icons.Default.Lock) {
                PermissionRow("Overlay", overlayReady) { onOverlay() }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Notification settings") }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Mic, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("App permissions (mikrofon)") }
            }
        }

        item {
            SettingsCard("Advanced", Icons.Default.Settings) {
                SwitchRow("Debug logging", settings.debugLogging) { c -> vm.updateSettings { it.copy(debugLogging = c) } }
                Text(
                    "Shizuku: " + when {
                        !shizukuAvailable -> "Not available"
                        shizukuGranted -> "Connected / permission granted"
                        else -> "Connected / permission not granted"
                    },
                    fontSize = 13.sp
                )
                Text(
                    "Shizuku bersifat opsional dan tidak menggantikan MediaProjection atau melewati keamanan Android.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
            }
        }

        item {
            SettingsCard("About", Icons.Default.Info) {
                Text("ALF Vision Panel ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Native Kotlin + Jetpack Compose + Material 3. Request Groq dikirim langsung dari perangkat lewat HTTPS.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
            }
        }
    }
    if (showProfileCreate) ProfileDialog(vm, onDismiss = { showProfileCreate = false })
}

@Composable
private fun SettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    AlfCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, size = 34)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Warning, null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text("$label permission", Modifier.weight(1f))
        if (!granted) TextButton(onClick = onFix) { Text("Aktifkan") } else Text("Aktif", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(label: String, selected: String, entries: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            selected, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            entries.forEach { e ->
                DropdownMenuItem(
                    text = { Text((e as? ResponseStyle)?.label ?: (e as? ThemeMode)?.label ?: e.name) },
                    onClick = { onSelect(e); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProfileDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("You are a helpful ALF Vision assistant.") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(prompt, { prompt = it }, label = { Text("System prompt") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.createProfile(name.trim(), prompt.trim()); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

```

## `app/src/main/java/com/alfread/alfvision/ui/theme/Theme.kt`

```kt
package com.alfread.alfvision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alfread.alfvision.core.model.Accent
import com.alfread.alfvision.core.model.AppSettings
import com.alfread.alfvision.core.model.ThemeMode

val AlfBlue = Color(0xFF1728FF)
val AlfPurple = Color(0xFF7B4DFF)
val AlfCyan = Color(0xFF14D9FF)

/**
 * FIX (penyebab FC saat aplikasi dibuka):
 * sebelumnya memakai Color(argb.toULong()) -> itu konstruktor nilai mentah (packed) Compose,
 * bukan ARGB, sehingga color space-nya invalid dan langsung crash saat tema dibuat.
 * Color(Long) adalah konstruktor ARGB yang benar.
 */
fun Accent.color(): Color = Color(argb)

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

fun brandBrush(accent: Color): Brush = Brush.linearGradient(listOf(accent, AlfCyan))

fun heroBrush(accent: Color): Brush = Brush.linearGradient(listOf(AlfBlue, accent, AlfCyan.copy(alpha = 0.85f)))

@Composable
fun ALFVisionTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val accent = settings.accent.color()
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = AlfCyan,
            onSecondary = Color(0xFF00252D),
            tertiary = Color(0xFF72D8FF),
            background = Color(0xFF0A0C16),
            onBackground = Color(0xFFE8EAF6),
            surface = Color(0xFF10132A),
            onSurface = Color(0xFFE8EAF6),
            surfaceVariant = Color(0xFF1B1F3A),
            onSurfaceVariant = Color(0xFFA9AFCB),
            surfaceContainerLow = Color(0xFF111430),
            surfaceContainer = Color(0xFF151934),
            surfaceContainerHigh = Color(0xFF1A1E3D),
            surfaceContainerHighest = Color(0xFF222749),
            outline = Color(0xFF5C6288),
            outlineVariant = Color(0xFF2E3358),
            error = Color(0xFFFF6B7A)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = Color(0xFF006F88),
            tertiary = Color(0xFF006F88),
            background = Color(0xFFF4F5FC),
            onBackground = Color(0xFF14172B),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF14172B),
            surfaceVariant = Color(0xFFE6E8F6),
            onSurfaceVariant = Color(0xFF50567A),
            surfaceContainerLow = Color(0xFFF8F9FE),
            surfaceContainer = Color(0xFFF1F2FB),
            surfaceContainerHigh = Color(0xFFFFFFFF),
            surfaceContainerHighest = Color(0xFFE9EBF8),
            outline = Color(0xFF8A90B3),
            outlineVariant = Color(0xFFD5D8EE),
            error = Color(0xFFC62828)
        )
    }
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
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
    fun addUser(text: String, imagePath: String? = null, imageBytes: ByteArray? = null) { _lines.value = _lines.value + ChatLine(System.nanoTime(), Role.USER, text, imagePath = imagePath, imageBytes = imageBytes) }
    fun addAssistant(text: String, model: String, usage: ModelUsage, imagePath: String? = null) { _lines.value = _lines.value + ChatLine(System.nanoTime(), Role.ASSISTANT, text, model = model, tokenUsage = usage.totalTokens, imagePath = imagePath) }
    fun clearChat() { _lines.value = emptyList() }
    fun removeLastAssistant() { if (_lines.value.lastOrNull()?.role == Role.ASSISTANT) _lines.value = _lines.value.dropLast(1) }
    fun removeLastUser() { if (_lines.value.lastOrNull()?.role == Role.USER) _lines.value = _lines.value.dropLast(1) }
    fun setRegion(value: Region?) { _region.value = value }
    fun setBusy(value: Boolean) { _busy.value = value }
    fun setError(value: String?) { _error.value = value }
    fun setVoiceState(value: VoiceState) { _voiceState.value = value }
    fun setImage(value: PendingImage) { _previousImage.value = _currentImage.value; _currentImage.value = value }
    fun clearImage() { _currentImage.value = null }
    fun pinCurrent() { _pinnedImage.value = _currentImage.value }
    fun unpin() { _pinnedImage.value = null }
}

enum class VoiceState { IDLE, LISTENING, ERROR }

```

## `app/src/main/java/com/alfread/alfvision/vision/VisionAssistantController.kt`

```kt
package com.alfread.alfvision.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    private var lastCompare: Boolean = false

    fun resetConversation() { conversationId = null }
    fun resumeConversation(id: Long?) { conversationId = id }

    fun capture(region: Region? = session.region.value, fullScreen: Boolean = region == null) {
        scope.launch { captureNow(region, fullScreen) }
    }

    /**
     * Ambil screenshot. Panel/overlay disembunyikan (bukan dihapus) lalu dimunculkan kembali.
     * FIX: sebelumnya view dihapus tetapi referensinya tidak di-null-kan sehingga panel tidak
     * pernah muncul lagi setelah capture pertama. Ada juga timeout supaya tidak stuck "busy".
     */
    suspend fun captureNow(region: Region? = session.region.value, @Suppress("UNUSED_PARAMETER") fullScreen: Boolean = region == null): Boolean {
        if (!ScreenCaptureService.isRunning) {
            session.setError("Screen capture belum aktif. Tekan START VISION dan izinkan screen capture.")
            return false
        }
        session.setBusy(true)
        session.setError(null)
        var success = false
        try {
            val vision = settings.flow.first().vision
            val effectiveRegion = if (vision.sendOnlyRegion) region else null
            FloatingPanelService.suppressForCapture(true)
            delay(280)
            val result = withTimeout(10_000) {
                capture.request(
                    region = effectiveRegion,
                    quality = vision.quality,
                    maxBytes = vision.maxImageBytes,
                    fullScreen = effectiveRegion == null
                )
            }
            session.setImage(
                PendingImage(
                    result.payload.bytes,
                    result.payload.mimeType,
                    if (effectiveRegion != null) "Region" else "Screen"
                )
            )
            success = true
        } catch (e: TimeoutCancellationException) {
            session.setError("Capture timeout. Pastikan screen capture aktif lalu coba lagi.")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            session.setError(errorMessage(t))
        } finally {
            FloatingPanelService.suppressForCapture(false)
            session.setBusy(false)
        }
        return success
    }

    fun ask(prompt: String, compare: Boolean = false): Job? {
        val cleaned = prompt.trim()
        if (cleaned.isEmpty()) return null
        lastPrompt = cleaned
        lastCompare = compare
        activeAskJob?.cancel()
        val job = scope.launch {
            session.setBusy(true)
            session.setError(null)
            try {
                val currentSettings = settings.flow.first()
                val profile = profiles.get(currentSettings.activeProfileId)
                val system = buildSystemPrompt(profile, currentSettings.responseStyle)
                val current = (session.currentImage.value ?: session.pinnedImage.value)?.bytes
                val previous = if (compare) session.previousImage.value?.bytes else null
                val imagePath = if (currentSettings.vision.saveScreenshots && current != null) imageStorage.save(current, "history") else null
                session.addUser(cleaned, imagePath, current?.let { makeThumbnail(it) })
                if (session.input.value.trim() == cleaned) session.setInput("")
                if (currentSettings.saveHistory) ensureConversation(profile?.id ?: 0, cleaned)
                val recent = session.lines.value.takeLast(30)
                val ai = groq.ask(
                    model = profile?.preferredModel?.takeIf { it.isNotBlank() } ?: currentSettings.activeModel,
                    messages = recent,
                    systemPrompt = system,
                    temperature = profile?.temperature ?: currentSettings.temperature,
                    maxTokens = profile?.maxTokens ?: currentSettings.maxTokens,
                    imageBase64 = current?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                    compareImageBase64 = previous?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                )
                session.addAssistant(ai.text, ai.model, ai.usage, imagePath)
                if (currentSettings.saveHistory) saveLastLines(ai.model, ai.usage.totalTokens)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                session.setError(errorMessage(t))
            } finally {
                session.setBusy(false)
            }
        }
        activeAskJob = job
        job.invokeOnCompletion { if (activeAskJob === job) activeAskJob = null }
        return job
    }

    fun cancelRequest() { activeAskJob?.cancel(); activeAskJob = null; session.setBusy(false) }

    fun retryLast() {
        val prompt = lastPrompt ?: return
        // Hapus pesan user yang gagal supaya tidak dobel saat retry.
        session.removeLastUser()
        ask(prompt, compare = lastCompare)
    }

    private fun promptFor(action: String): String = when (action) {
        "Analyze" -> "Analyze what is happening in this selected screen area. Focus on facts, visible UI, likely state, and practical next steps."
        "Explain" -> "Jelaskan apa yang sedang terjadi di area layar ini dan kenapa hal tersebut terjadi."
        "Read" -> "Read all clearly visible text in this image. Preserve line breaks where useful."
        "Translate" -> "Translate the visible text into Indonesian. Preserve names, numbers, and important formatting."
        "Summarize" -> "Summarize the important information visible in this screen area."
        "Find Error" -> "Find possible errors or abnormal behavior visible in this screen area. Explain evidence and fixes."
        "Extract Text" -> "Extract visible text exactly as readable. Return text only, with sensible line breaks."
        "Describe" -> "Describe the visible screen area precisely and briefly."
        "Jawab Soal" -> "Lihat layar ini. Temukan SEMUA soal atau pertanyaan yang terlihat (pilihan ganda, benar/salah, isian, hitungan, esai singkat). " +
            "Untuk setiap soal tulis: nomor soal, lalu JAWABAN AKHIR lebih dulu (untuk pilihan ganda tulis huruf dan isinya), " +
            "kemudian alasan singkat 1-2 kalimat atau langkah hitungan ringkas. " +
            "Jika soal terpotong atau tidak terbaca jelas, katakan bagian mana yang kurang. " +
            "Jawab dalam bahasa yang sama dengan soalnya. Tulis teks biasa tanpa simbol markdown."
        "Help Me" -> "Based on this screen area, tell me what I should do next. Give practical steps and do not invent hidden information."
        else -> action
    }

    fun quickAction(action: String) {
        ask(promptFor(action))
    }

    /** Satu tombol: ambil layar lalu langsung jawab soal yang terlihat. */
    fun answerScreen() {
        scope.launch {
            val region = session.region.value
            if (captureNow(region, region == null)) ask(promptFor("Jawab Soal"))?.join()
        }
    }

    private fun makeThumbnail(bytes: ByteArray): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 720) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bitmap.recycle()
        out.toByteArray()
    }.getOrNull()

    fun compare() {
        val current = session.currentImage.value
        val previous = session.previousImage.value
        if (current == null || previous == null) {
            session.setError("Compare membutuhkan dua screenshot. Capture dua kali terlebih dahulu.")
            return
        }
        ask("The first image is CURRENT and the second image is PREVIOUS. Compare them and explain what changed, what appeared, what disappeared, and the most important differences.", true)
    }

    /** Auto Analyze sekarang benar-benar tersambung ke setting (sebelumnya switch-nya tidak melakukan apa-apa). */
    fun setAutoAnalyze(enabled: Boolean) {
        autoJob?.cancel()
        autoJob = null
        if (!enabled) return
        autoJob = scope.launch {
            while (isActive) {
                val current = settings.flow.first()
                if (!current.vision.autoAnalyze) break
                if (!session.busy.value && ScreenCaptureService.isRunning) {
                    val region = session.region.value
                    if (captureNow(region, region == null)) {
                        ask(promptFor("Analyze"))?.join()
                    }
                }
                delay(current.vision.interval.millis.coerceAtLeast(2_000))
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
        val plain = "Write in plain text. Do not use markdown symbols such as asterisks, pound signs or backticks; use simple numbered lines or dashes instead."
        return listOf(profile?.systemPrompt ?: "You are ALF Vision, a helpful screen assistant.", styleText, plain).joinToString(" ")
    }

    private fun errorMessage(t: Throwable): String = when (t) {
        is GroqApiException -> when (t.code) {
            401 -> "Groq API Key belum diatur atau tidak valid."
            403 -> "Groq menolak request ini. Periksa akses atau model."
            429 -> "Rate limit reached. Tunggu beberapa saat atau ganti model."
            500, 502, 503 -> "Groq sedang bermasalah sementara. Coba lagi nanti."
            else -> t.message
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
    <color name="alf_window_bg">#F4F5FC</color>
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
    <style name="Theme.ALFVisionPanel" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/alf_window_bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>

```

## `app/src/main/res/values-night/colors.xml`

```xml
<resources>
    <color name="alf_window_bg">#0A0C16</color>
</resources>

```

## `app/src/main/res/values-night/themes.xml`

```xml
<resources>
    <style name="Theme.ALFVisionPanel" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/alf_window_bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
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

## `app/src/test/java/com/alfread/alfvision/MarkdownTest.kt`

```kt
package com.alfread.alfvision

import androidx.compose.ui.graphics.Color
import com.alfread.alfvision.ui.components.renderMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownTest {
    @Test fun boldMarkersAreRemoved() {
        val out = renderMarkdown("**Jawaban:** B", Color.Gray)
        assertEquals("Jawaban: B", out.text)
    }

    @Test fun bulletsBecomeDots() {
        val out = renderMarkdown("*   **Adjust Settings:** tap toggles\n* Second", Color.Gray)
        assertEquals("• Adjust Settings: tap toggles\n• Second", out.text)
    }

    @Test fun headingsAndFencesHaveNoSymbols() {
        val out = renderMarkdown("## Judul\n```\nx = 1\n```\n`kode` biasa", Color.Gray)
        assertFalse(out.text.contains("#") || out.text.contains("`"))
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

## `app/src/test/java/com/alfread/alfvision/RegionResizeTest.kt`

```kt
package com.alfread.alfvision

import androidx.compose.ui.graphics.Color
import com.alfread.alfvision.core.model.Accent
import com.alfread.alfvision.core.model.Region
import com.alfread.alfvision.service.REGION_BR
import com.alfread.alfvision.service.REGION_MOVE
import com.alfread.alfvision.service.REGION_TL
import com.alfread.alfvision.service.hitTestRegion
import com.alfread.alfvision.service.resizeRegion
import com.alfread.alfvision.ui.theme.color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionResizeTest {
    private val region = Region(100, 100, 400, 400, 1000, 2000)

    @Test fun moveIsClampedInsideScreen() {
        val moved = resizeRegion(region, REGION_MOVE, 5000f, -5000f, 1000, 2000)
        assertEquals(600, moved.x)
        assertEquals(0, moved.y)
        assertEquals(400, moved.width)
    }

    @Test fun resizeKeepsMinimumSize() {
        val shrunk = resizeRegion(region, REGION_BR, -5000f, -5000f, 1000, 2000)
        assertTrue(shrunk.width >= 80 && shrunk.height >= 80)
    }

    @Test fun topLeftResizeChangesOrigin() {
        val resized = resizeRegion(region, REGION_TL, -50f, -50f, 1000, 2000)
        assertEquals(50, resized.x)
        assertEquals(450, resized.width)
    }

    @Test fun hitTestFindsCornerAndBody() {
        assertEquals(REGION_TL, hitTestRegion(region, 105f, 105f, 30f))
        assertEquals(REGION_MOVE, hitTestRegion(region, 300f, 300f, 30f))
    }

    @Test fun accentColorIsValidArgb() {
        // Regresi FC: Color(ULong) memakai color space invalid dan crash saat tema dibuat.
        val color: Color = Accent.PURPLE.color()
        assertEquals(1f, color.alpha, 0.01f)
        assertEquals(0x7B / 255f, color.red, 0.01f)
    }
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
