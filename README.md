# ALF Voice Control

ALF Voice Control is an offline-first Android voice-control app built with Kotlin, Jetpack Compose and Material 3.

Package: `com.alfread.alfvoicecontrol`

## What it actually does

- Stores voice commands, settings, wake-word sample and recordings locally.
- Records voice samples into the app's private internal storage.
- Uses Android `SpeechRecognizer` for command recognition.
- Prefers Android's on-device speech recognizer on API 31+ when an on-device engine/language model is available.
- Falls back to the system speech recognizer when on-device recognition is unavailable. The fallback engine may require network access outside ALF's control.
- Matches recognized text to saved trigger phrases with normalization, token overlap and bounded Levenshtein similarity.
- Runs continuous wake-word/command sessions from a microphone foreground service. This is implemented as repeated short speech-recognition sessions, not as a proprietary always-on hardware hotword engine.
- Can wake the display with Android power APIs where the device/OEM allows it.
- Can turn the screen off through Android Device Administrator `DevicePolicyManager.lockNow()` after the user explicitly enables ALF's Device Administrator permission.
- Can launch installed launcher-visible applications without hardcoded package names.
- Uses no Firebase, backend, login or `INTERNET` permission.
- Stores the optional ALF PIN as a salted SHA-256 hash; it is not an Android lock-screen replacement.

## Important Android limitations

### Wake word
Android's public `SpeechRecognizer` API is a speech recognition API rather than a dedicated low-power always-on keyword detector. ALF therefore uses repeated short recognition sessions while its microphone foreground service is active. Reliability, battery use and OEM background behavior vary by device.

### On-device recognition
On Android 12/API 31+, ALF attempts `SpeechRecognizer.createOnDeviceSpeechRecognizer()` first. If the device does not expose an on-device recognizer/language model, ALF uses the normal system recognizer. ALF itself does not upload recordings to a server.

### Screen on
ALF uses the public power/screen APIs available to ordinary applications. It does not bypass the keyguard. If Android/OEM policy refuses the wake request, ALF reports failure instead of claiming success.

### Screen off
The Screen Off command requires the user to enable ALF's Device Administrator policy for force lock. ALF never reads or bypasses the Android PIN/pattern/password.

### Opening another app
Android can block background activity launches even when a foreground service is running. If the package exists but Android refuses the launch, ALF reports that it was blocked.

### Reboot
Android 14+ places strict limits on starting microphone foreground services from boot/background because `RECORD_AUDIO` is a while-in-use permission. After reboot, ALF therefore posts a reminder instead of attempting an illegal microphone-service start.

### Battery optimization / OEM behavior
Some OEMs are more aggressive about stopping background work. Use the Background Operation screen to open Android battery settings and configure ALF according to the options available on the phone.

## First run

1. Open ALF Voice Control.
2. Grant microphone permission when requested.
3. Grant notification permission on Android 13+.
4. Set the wake phrase; the default is `Alf`.
5. Optionally create an ALF PIN.
6. Start listening while ALF is visible.

## Command examples

- `Alf bangun` -> Screen On
- `Alf tidur` -> Screen Off (requires Device Administrator)
- `Buka WhatsApp` -> Launch the selected installed application

You can also configure the wake word separately. For example, with wake word `Alf`, saying `Alf buka WhatsApp` can be handled in a single recognition session when the transcript contains both wake word and command.

## Voice recordings

Voice samples are saved in the app-private `files/voice` directory. They are never uploaded by ALF. The saved audio is a user-owned reference/recording feature; command matching still uses speech-to-text. ALF does not claim speaker verification or biometric voice authentication.

## Build locally

Requirements:

- JDK 17
- Internet access for the first Gradle bootstrap/dependency download
- Android SDK with API 35

From the project root:

```bash
chmod +x ./gradlew
./gradlew assembleDebug
```

The project includes `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, and `gradlew.bat`. The wrapper bootstraps and caches the pinned Gradle 8.10.2 distribution when necessary. The pinned Android Gradle Plugin is 8.8.2 and the project targets SDK 35.

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Workflow file:

```text
.github/workflows/build.yml
```

It:

1. checks out the repository;
2. installs JDK 17;
3. configures Android SDK;
4. installs platform 35, platform-tools and build-tools 35.0.0;
5. makes `gradlew` executable;
6. runs `./gradlew assembleDebug`;
7. uploads the debug APK as a workflow artifact.

No `local.properties`, secret, Firebase project or server is required.

## Install the APK

Download the `ALF-Voice-Control-debug` artifact from a successful GitHub Actions run, extract the APK, then install it on the Android phone. Android may require allowing installation from the source used to open the APK.

## Device Administrator setup

Open:

`Settings -> Device Administrator` inside ALF.

Tap enable and confirm the force-lock policy. This permission is used only for the Screen Off command.

To revoke it later, disable ALF in Android's device-admin settings or from the system confirmation flow.

## Security notes

ALF intentionally does not contain code for:

- bypassing Android lock screen authentication;
- reading Android PIN/pattern/password data;
- exploiting Accessibility or hidden APIs to bypass security;
- root exploits;
- silent/unapproved microphone access;
- hidden background behavior without a foreground-service notification.

The Android system remains authoritative over the lock screen, background starts, microphone privacy controls and OEM restrictions.
