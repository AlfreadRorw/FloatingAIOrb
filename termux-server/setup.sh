#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "[1/4] Updating package index..."
pkg update -y

echo "[2/4] Installing Python and FFmpeg..."
pkg install -y python ffmpeg

echo "[3/4] Checking yt-dlp..."
if command -v yt-dlp >/dev/null 2>&1; then
    echo "yt-dlp already installed: $(yt-dlp --version)"
else
    echo "yt-dlp command not found."
    echo "Try: pkg search yt-dlp"
    echo "If your repository provides it, run: pkg install yt-dlp"
    echo "Otherwise run: python -m pip install yt-dlp"
fi

echo "[4/4] Creating download directory..."
termux-setup-storage || true
mkdir -p "/storage/emulated/0/Download/ALF Downloader"

echo
echo "Setup finished."
echo "Start with:"
echo "  bash start.sh"
