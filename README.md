# WhatsStatus Vault

Offline Android app for collecting WhatsApp status videos into the device Download folder.

## Default paths
Source:
`/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/`

Destination:
`/storage/emulated/0/Download/`

## Features
- Black/white UI.
- Status video scanner and thumbnails.
- Select one or many videos.
- Download one or all selected videos.
- Move mode by default; optional copy mode in Settings.
- Duplicate-safe destination names: `name (1).mp4`, etc.
- Media scan after transfer so the saved video is discoverable by gallery/media apps.
- Download history persisted locally.
- Permission/status screen and direct shortcut to storage access settings.
- Dock: Status, History, Settings.
- No account or server required.

## Storage note
This build requests `MANAGE_EXTERNAL_STORAGE` because the core feature directly manages files under shared storage and the exact WhatsApp `.Statuses` path. Android documents `/Android/media` as shared storage and permits direct path access with this special access. Google Play applies policy restrictions to this permission, so this project is suited to direct APK/sideload distribution unless its use fits Play's permitted categories.

## Build locally
```bash
./gradlew :app:assembleDebug
```
APK:
`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
Push to GitHub and run **Build WhatsStatusVault APK**. The workflow uploads `WhatsStatusVault-debug` as an artifact.
