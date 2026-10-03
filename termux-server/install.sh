#!/data/data/com.termux/files/usr/bin/bash
set -e
echo "[1/4] Updating packages"
pkg update -y
echo "[2/4] Installing Python + FFmpeg + Node.js"
pkg install -y python ffmpeg nodejs
echo "[3/4] Installing Python packages"
python -m pip install --no-cache-dir flask yt-dlp
echo "[4/4] Preparing storage"
termux-setup-storage || true
mkdir -p "/storage/emulated/0/Download/ALF Downloader"
echo "Installation complete."
python --version
yt-dlp --version
ffmpeg -version | head -n 1
