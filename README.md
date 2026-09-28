# FloatSpace

FloatSpace is a polished Android edge launcher / freeform workspace shell.

## What it does

- Persistent white edge handle on the left or right side of the screen.
- Tap the edge handle to open a fast all-app drawer.
- Search installed launcher apps.
- Pin/favorite apps for a dedicated PINNED tab.
- Launch multiple apps through Android freeform window mode when the device supports it.
- Optional Shizuku integration for shell-level `am start` and `am task resize` commands.
- Workspace controls for the most recently launched freeform task: focus, resize smaller/larger, move left/up/down/right, and force-stop.
- Edge length, side, haptic feedback, boot startup, glow theme, battery settings and freeform compatibility settings.
- Optional accessibility helper for OEMs that expose window-state events differently. It does not inspect app content.

## Important Android limitation

A normal third-party app cannot directly render another installed app's UI inside its own overlay view. FloatSpace therefore uses Android's real freeform task/window manager where the device exposes it. Android documents `TYPE_APPLICATION_OVERLAY` for the edge UI, while real freeform task launch/resize is driven here through the shell identity supplied by Shizuku.

On some phones the OEM blocks freeform windows, changes the shell command behavior, or provides its own mini-window implementation. In that case FloatSpace can still act as the edge launcher, but third-party app windows may open full-screen or refuse freeform placement.

## Shizuku

Install and start Shizuku, then grant FloatSpace its permission. Shizuku's official API documentation supports adding the `api` and `provider` dependencies and describes shell/user-service access. The project uses Shizuku 13.1.5.

Recommended device setup when available:

1. Start Shizuku.
2. Grant FloatSpace Shizuku permission.
3. Grant FloatSpace "Display over other apps".
4. On devices exposing them, enable "freeform windows" and "force activities to be resizable" in Developer Options.
5. Disable aggressive battery optimization for FloatSpace if the edge handle is killed in the background.

## Build

The project is prepared for Gradle 8.9, Android Gradle Plugin 8.7.3, compileSdk 35 and targetSdk 35.

GitHub Actions is configured in `.github/workflows/build.yml` and uploads a debug APK artifact.

## Safety / permissions

FloatSpace does not read the content of other apps. The accessibility service is optional and does not use `AccessibilityNodeInfo` or screen scraping.
