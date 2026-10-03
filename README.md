# ALF Downloader

ALF Downloader is an Android client + Termux local server for downloading content that you are authorized to save.

## Architecture

Android app
-> HTTP localhost `127.0.0.1:8080`
-> Flask server in Termux
-> yt-dlp + FFmpeg
-> `/storage/emulated/0/Download/ALF Downloader`

The server binds only to `127.0.0.1`.

## Requirements

- Android 7.0+ (API 24)
- Android Studio with JDK 17
- Termux from a source that supports external-app commands
- Python 3
- FFmpeg
- yt-dlp
- Termux shared-storage permission

## 1. Termux setup

Open Termux and run:

```bash
termux-setup-storage
pkg update
pkg install python ffmpeg
```

Install yt-dlp using the package available in your Termux repository:

```bash
pkg search yt-dlp
```

If `yt-dlp` is available:

```bash
pkg install yt-dlp
```

If your repository does not provide it, use a normal Python package install without upgrading Termux's pip:

```bash
python -m pip install yt-dlp
```

Do NOT run `python -m pip install --upgrade pip` in Termux.

Then:

```bash
cd ~/Github
mkdir -p termux-server
```

Copy the `termux-server` folder from this project to:

```text
~/Github/termux-server
```

Start it manually once:

```bash
cd ~/Github/termux-server
bash start.sh
```

Test:

```bash
curl http://127.0.0.1:8080/api/health
```

Expected:

```json
{"ok":true,"service":"ALF Downloader","version":"1.0.0"}
```

## 2. Optional automatic server start from Android

The app includes a "Start Termux Server" button.

The Termux side must allow external apps. In Termux:

```bash
mkdir -p ~/.termux
nano ~/.termux/termux.properties
```

Add:

```text
allow-external-apps=true
```

Restart Termux after changing this setting.

The Android app uses:

```text
com.termux.RUN_COMMAND
```

and starts:

```text
/data/data/com.termux/files/home/Github/termux-server/start.sh
```

If your server is stored elsewhere, change `SERVER_SCRIPT` in:

```text
android/app/src/main/java/com/alfread/alfdownloader/MainActivity.kt
```

## 3. Build with GitHub Actions

Push the project to GitHub.

The workflow is:

```text
.github/workflows/build.yml
```

It installs JDK 17, Gradle 8.9 and builds:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

The APK is uploaded as a workflow artifact.

## 4. Android app

The app provides:

- Server status
- Start Termux server
- URL input
- Metadata/info lookup
- Quality selection
- Download
- Download progress
- Download history
- Settings
- Localhost cleartext networking
- Termux RUN_COMMAND integration

## 5. API

### Health

```http
GET /api/health
```

### Video info

```http
POST /api/info
Content-Type: application/json

{"url":"https://example.com/video"}
```

### Start download

```http
POST /api/download
Content-Type: application/json

{
  "url":"https://example.com/video",
  "quality":"720p"
}
```

Quality values:

- `best`
- `1080p`
- `720p`
- `480p`
- `audio`

### Job status

```http
GET /api/jobs/<job_id>
```

### History

```http
GET /api/history
```

## Content authorization

Use this project only for content you are allowed to download. It does not implement DRM bypass, private-account access, authentication bypass, paywall bypass, or other access-control circumvention.
