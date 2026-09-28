# ARACHNE Android

ARACHNE is the Android version of the offline kinetic clock.

## Features
- Full-screen WebView version of the ARACHNE clock.
- 7 color themes.
- 24/12 hour mode.
- Roman/Arabic numerals.
- World clock.
- Stopwatch.
- Ambient mode.
- LocalStorage settings.
- Home-screen 1×1 widget.
- Widget opens the app when tapped.
- No network permission.
- No external web libraries.

## GitHub Actions
Push this project to GitHub and open **Actions → Build ARACHNE APK**.
The workflow builds `ARACHNE-debug.apk` and uploads it as an artifact.

## Widget
Add **ARACHNE** from Android's widget picker and choose the smallest 1×1 square size.

Note: Android launchers control the final widget pixel size and update scheduling. The app itself has smooth animation; the home-screen widget uses lightweight minute/second text to remain battery-friendly.
