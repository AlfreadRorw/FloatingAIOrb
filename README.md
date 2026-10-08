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
