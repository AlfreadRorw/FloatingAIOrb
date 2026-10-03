#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "[1/4] Updating packages"
pkg update -y
echo "[2/4] Installing Python + FFmpeg + Node"
pkg install -y python ffmpeg nodejs termux-api
echo "[3/4] Installing Python packages"
python -m pip install -U pip
python -m pip install -U "yt-dlp[default]" flask waitress
echo "[4/4] Installing ALF Downloader server"
mkdir -p "$HOME/alf-downloader-server"
cp "$HERE/server.py" "$HOME/alf-downloader-server/server.py"
cp "$HERE/start.sh" "$HOME/alf-downloader-server/start.sh"
chmod +x "$HOME/alf-downloader-server/start.sh"
mkdir -p "/storage/emulated/0/Download/ALF Downloader" || true

mkdir -p "$HOME/.termux"
touch "$HOME/.termux/termux.properties"
grep -q '^allow-external-apps' "$HOME/.termux/termux.properties" \
  && sed -i 's/^allow-external-apps.*/allow-external-apps=true/' "$HOME/.termux/termux.properties" \
  || echo "allow-external-apps=true" >> "$HOME/.termux/termux.properties"
termux-reload-settings 2>/dev/null || true

echo
echo "Server installed at: $HOME/alf-downloader-server"
echo "allow-external-apps=true sudah diaktifkan."
echo "Start manual: bash ~/alf-downloader-server/start.sh"
