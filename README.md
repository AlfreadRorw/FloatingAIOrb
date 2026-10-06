# ALF Auto Trigger

Native Android auto-trigger/macro controller with a movable edge handle, smart panel, local macro library, Shizuku UserService, low-level touchscreen recording attempt, and gesture playback.

## Features
- White edge handle, draggable vertically.
- Drag horizontally to move the handle between left/right edges.
- Tap handle to open the smart panel.
- Record touch gestures through Shizuku when the device exposes the touchscreen input device to the shell identity.
- Saves tap/swipe/delay sequences locally as JSON.
- One-tap macro playback from the edge panel.
- Loop and speed values are stored per macro.
- Accessibility gesture playback is preferred; Shizuku `input tap/swipe` is a fallback.
- Native Android Views, no WebView.

## Important Android limitation
Android's public Accessibility API can dispatch gestures but does not provide a universal API for capturing every raw touch event occurring inside other apps. ALF therefore attempts a Shizuku shell-side `getevent` recording path. OEM/kernel permissions vary; when the touchscreen event node is not readable by the Shizuku shell identity, the raw recorder cannot work on that device without additional privileges.

## Build
JDK 17, Android SDK 36. GitHub Actions uses Gradle 8.11 through the official Gradle setup action.
