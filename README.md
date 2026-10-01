# ALF PET

Offline digital pet game for Android, built with Kotlin, Jetpack Compose and Material 3.

## Requirements

- Android Studio with Android SDK 35
- JDK 17
- Gradle 8.9 or newer compatible with AGP 8.7.3

## Features

- 8-frame local cat idle animation using `idle_01.png` through `idle_08.png`
- Hunger, Happiness, Energy and Cleanliness systems
- Mood calculation
- Coins and XP
- Progressive levels with increasing XP requirements
- Offline time decay based on `lastUpdate`
- Local persistence with SharedPreferences
- Feed, Play, Sleep and Clean actions
- Button press and pet interaction animations
- Level-up overlay
- Responsive portrait/landscape-friendly Compose layout
- No Firebase, server, login or gameplay network access

## Build

```bash
gradle assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Assets

The supplied cat sprite sheet was split into the required eight drawable frames without renaming the frame names. The black background around each frame was made transparent so the cat integrates cleanly into the room.

## GitHub Actions

The workflow in `.github/workflows/build.yml` installs Android SDK 35, configures JDK 17 and Gradle, builds the debug APK, and uploads the artifact as `ALF-PET-debug-apk`.
