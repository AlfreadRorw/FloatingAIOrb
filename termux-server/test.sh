#!/data/data/com.termux/files/usr/bin/bash
curl -s http://127.0.0.1:8080/api/health
echo
yt-dlp --version
ffmpeg -version | head -n 1
