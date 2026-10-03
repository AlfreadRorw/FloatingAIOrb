#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "[1/4] Updating packages"
pkg update -y
echo "[2/4] Installing Python + FFmpeg"
pkg install -y python ffmpeg nodejs
echo "[3/4] Installing Python packages"
python -m pip install -U pip
python -m pip install -U "yt-dlp[default]" flask
echo "[4/4] Installing ALF Downloader server"
mkdir -p "$HOME/alf-downloader-server"
cp "$HERE/server.py" "$HOME/alf-downloader-server/server.py"
cp "$HERE/start.sh" "$HOME/alf-downloader-server/start.sh"
chmod +x "$HOME/alf-downloader-server/start.sh"
mkdir -p "/storage/emulated/0/Download/ALF Downloader"

echo
echo "Server installed at: $HOME/alf-downloader-server"
echo "Set allow-external-apps=true in ~/.termux/termux.properties"
echo "Start manually: bash ~/alf-downloader-server/start.sh"
