# ALF Downloader

Android client + Termux local server for downloading media from sources supported by yt-dlp, such as YouTube/TikTok, for content you are authorized to download. It does not bypass DRM, paywalls, login protection, or other access controls.

## Features
- Monochrome black/white Android UI
- Termux local backend on 127.0.0.1:8080
- Start Termux server from the app via Termux RUN_COMMAND
- URL metadata lookup
- Quality presets: Best, 1080p, 720p, 480p, 360p, Audio
- Download queue with progress polling
- Cancel running job
- Local history stored on Android
- Download folder: /storage/emulated/0/Download/ALF Downloader
- GitHub Actions debug APK build

## First Termux setup
1. Install Termux from a trusted/current source.
2. In Termux run `termux-setup-storage`.
3. Run the commands in `termux-server/INSTALL.txt`.
4. Enable external app commands in `~/.termux/termux.properties`:
   `allow-external-apps=true`
5. Android Settings -> Apps -> ALF Downloader -> Additional permissions -> Run commands in Termux environment -> allow.
6. Open ALF Downloader -> Settings -> Start Termux Server.

The RUN_COMMAND integration is the official mechanism documented by Termux for third-party apps. It requires the RUN_COMMAND permission and `allow-external-apps=true`; Android 11+ apps also need package visibility for `com.termux`.

## Logo assets
Put your own PNG in the Android resources as needed:
- App logo: 1024x1024 PNG, transparent background
- Adaptive icon foreground: 432x432 PNG
- Tab icons: 96x96 PNG if you replace the built-in vector icons
Recommended style: monochrome, high contrast, no text smaller than the icon can display.

## YouTube support note
Current yt-dlp documentation says full YouTube support uses the `yt-dlp-ejs` package plus a supported JavaScript runtime. This project installs `yt-dlp[default]` and Node.js in Termux, then configures the server to use Node for EJS.

## Auto-start note
The app uses Termux's documented `RUN_COMMAND` service. Android requires the app to have `com.termux.permission.RUN_COMMAND`, and Termux requires `allow-external-apps=true`. The app also declares package visibility for `com.termux`. On some devices, battery/background restrictions can still interfere with Termux; exempting Termux from aggressive battery optimization can help.
