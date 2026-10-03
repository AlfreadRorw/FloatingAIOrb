# ALF Downloader
Android + Termux local downloader.

Android app -> http://127.0.0.1:8080 -> Flask -> yt-dlp + FFmpeg -> Download/ALF Downloader/

## Termux
```bash
cd ~/Github/termux-server
bash install.sh
bash start.sh
```
Test:
```bash
curl http://127.0.0.1:8080/api/health
```

For Android -> Termux RUN_COMMAND, set `allow-external-apps=true` in `~/.termux/termux.properties`.
Use only for media you are authorized to download and do not bypass DRM, authentication, private-content restrictions, or access controls.
