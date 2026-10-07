# ALF Vision Panel

ALF Vision Panel is a native Android AI screen assistant built with Kotlin, Jetpack Compose, Material 3, MediaProjection, WindowManager overlays, Room, DataStore, Android Keystore, Coroutines, and OkHttp.

The main workflow is:

`Other app/game -> floating ALF panel -> region selector -> MediaProjection -> ImageReader -> crop/resize/compress -> Groq vision -> response in floating panel`

No WebView, Firebase, cloud database, custom backend, account login, or hardcoded Groq API key is used.

## Features

- Native floating panel using `TYPE_APPLICATION_OVERLAY`
- Floating AI Orb when minimized
- MediaProjection + VirtualDisplay + ImageReader screen capture
- Movable/resizable region selector with corner handles
- Region presets with save, rename, duplicate, activate, delete
- Groq API key protected by Android Keystore; only encrypted ciphertext is stored in app preferences
- Groq model selector with dynamic `/models` loading
- Vision chat and normal text chat
- Manual capture by default; optional auto-analyze intervals from 0.5s to 30s
- Quick actions: Analyze, Explain, Read, Translate, Summarize, Find Error, Extract Text, Describe, Help Me
- AI profiles: General, Coding, Gaming, Translator, Android, Minecraft, MLBB, Homework, and custom profiles
- Response styles: Short, Normal, Detailed, Technical, Step-by-step
- Screenshot annotation: rectangle, circle, arrow, line, text, blur/pixelation, crop, undo, clear
- Before/After screenshot comparison
- SpeechRecognizer voice input when microphone permission is granted
- Local Room history
- Privacy controls for history, screenshots, and auto-delete
- Optional Shizuku presence detection without depending on it for core functionality
- Gaming mode compact AI / Capture / Ask controls
- Accessibility labels and large touch targets

## Requirements

- Android 7.0+ (API 24)
- Target SDK 35
- JDK 17 for Android builds
- Android SDK Platform 35 and Build Tools 35.0.0
- A Groq API key for cloud AI analysis

## Groq API Key

Open **Settings -> AI** and paste a key manually. The app encrypts the key with an AES-GCM key kept in Android Keystore. The raw key is not written to Logcat, notifications, DataStore, or the clipboard.

The current default vision model is `qwen/qwen3.8-27b`. Groq's model catalog and vision documentation should be checked when choosing another model because availability can change.

## Permissions

- **Overlay:** required for the floating panel and region selector.
- **Screen capture:** granted through the Android MediaProjection consent dialog.
- **Microphone:** optional voice input.
- **Notifications:** recommended so foreground capture state is visible on Android 13+.

## Screen Capture

Press **START VISION**. The app opens the overlay permission page when needed, then requests MediaProjection consent. Once approved, `ScreenCaptureService` runs as a media-projection foreground service.

Press **STOP CAPTURE** or the foreground notification's **STOP** action to release ImageReader, VirtualDisplay, and MediaProjection.

The app does not silently start screen capture and does not send screenshots without a user-triggered AI request. With region mode enabled, only the selected region is sent.

## Privacy

By default:

- screenshots are processed in memory and not permanently stored;
- conversations may be stored locally in Room unless disabled;
- cloud AI requests are sent directly from the device to Groq using HTTPS;
- no ALF server stores your API key.

## Build locally

```bash
git clone <your-repository-url>
cd ALF-Vision-Panel
./gradlew lint
./gradlew test
./gradlew assembleDebug
```

No `local.properties` file is required by the project. Android SDK discovery is left to the local Android/Gradle environment.

## GitHub Actions

Workflow: `.github/workflows/build.yml`

It:

1. checks out the repository;
2. installs JDK 17;
3. installs Android SDK 35;
4. enables Gradle caching;
5. runs lint;
6. runs unit tests;
7. builds `assembleDebug`;
8. uploads `ALF-Vision-Debug`.

For release signing, configure these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The workflow creates the keystore file at runtime and never commits it.

## Architecture

```text
ui/
  navigation/
  screens/
  components/
  theme/

core/
  AppContainer
  EventBus
  RegionState

service/
  FloatingPanelService
  ScreenCaptureService

data/
  local/ Room database + DAOs + entities
  network/ Groq HTTP client + models
  prefs/ DataStore preferences
  secure/ Android Keystore storage
  repository/ application repositories

domain/
  RegionCalculator
  ImageProcessor
  PromptBuilder
  ShizukuCompat
```

`FloatingPanelService` owns the overlay windows. AI/network logic lives in `GroqRepository` and `GroqApiClient`. Screen capture owns only MediaProjection, VirtualDisplay, and ImageReader. This separation keeps service lifecycle code from becoming the AI layer.

## Troubleshooting

### Overlay does not appear

Open Android **Settings -> Apps -> Special app access -> Display over other apps** and enable ALF Vision Panel. Then return to the app and retry.

### MediaProjection fails

Tap **STOP VISION**, start again, and approve the Android screen-capture consent dialog. If the phone revoked the projection token after a system rotation or another capture app took the projection, repeat the flow.

### API key invalid

Use Settings -> AI -> Delete, paste the key again, Save, and run Test Connection. The app displays a user-facing authentication error without showing the Authorization header.

### Model unavailable

Use Settings -> AI -> Refresh Models and select an active model returned by Groq. Vision requests require a model that supports image input.

### Rate limit

The app uses bounded exponential backoff for 429 and selected 5xx responses. It never performs infinite retries.

### Shizuku not detected

Shizuku is optional. The core app remains fully functional without it. The application only detects the installed Shizuku package and does not claim to bypass Android security restrictions.

### GitHub Actions fails

Check the first failing step. Common causes are Gradle/network service interruptions, Android SDK package availability, or a malformed signing secret. Debug builds do not require signing secrets.

### Gradle fails locally

Use JDK 17, ensure Android SDK Platform 35 and Build Tools 35.0.0 are installed, and run `./gradlew --version` before building.

## Security Notes

Never commit a Groq API key or release keystore. Do not enable debug logging for sensitive workflows. The internal logger redacts bearer-token-looking values and does not log screenshots or full sensitive prompts.

## License

The project is structured as an open-source-ready application. Add the license you want before public distribution.

## Verification

The source tree has been checked for Kotlin/resource reference consistency, Android manifest/service declarations, Gradle configuration, GitHub Actions configuration, API-key leakage patterns, and missing incomplete markers.

The build environment used to package this project does not provide an Android SDK or network access to download the Gradle distribution/dependencies, so `lint`, unit tests, and APK assembly were not executable inside that environment. The repository includes the complete Gradle wrapper configuration and a GitHub Actions workflow that performs those checks on a hosted runner.

## Open-source libraries

The project uses AndroidX, Jetpack Compose, Material 3, Room, DataStore, Kotlin Coroutines, OkHttp, and Kotlin tooling. These libraries are distributed under their respective upstream open-source licenses, primarily Apache License 2.0. Check each dependency's published license before redistribution.

## Privacy model

Screen capture is initiated only after the Android MediaProjection consent flow. In selected-region mode, the captured full frame is cropped locally before the image is placed into the Groq request. The app has no custom backend, so the API key and request go directly from the device to the configured Groq endpoint over HTTPS.
