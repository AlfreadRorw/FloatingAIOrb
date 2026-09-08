# Floating AI Orb — Android Kotlin

Prototype Android app with:

- Floating AI orb over other apps/games
- Android MediaProjection screen capture
- Foreground service for capture
- AI panel opened without leaving the current app
- Android 8+ support

## Requirements

- Android Studio with Android SDK 35
- JDK 17
- A real Android phone is recommended for testing

## Run

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Build and run on an Android device.
4. Open the app.
5. Grant "Display over other apps".
6. Tap "Mulai Screen Capture" and accept Android's screen-capture confirmation.
7. Return to a game/app. The floating orb should remain visible.
8. Tap the orb to open the AI panel.

## Important

This is the foundation, not a production AI service.

The next layer is:

Screen frame -> image preprocessing -> AI Vision API/local model -> structured game analysis -> response -> overlay.

Do not hard-code API keys into the APK. Use a secure backend or an appropriate runtime secret mechanism.

Some games/apps can block screenshots or overlays. Android system UI, DRM-protected content, and certain secure windows may not be capturable.
