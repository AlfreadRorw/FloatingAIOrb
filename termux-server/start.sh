#!/data/data/com.termux/files/usr/bin/bash
set -e

cd "$(dirname "$0")"

export PYTHONUNBUFFERED=1

echo "========================================"
echo " ALF Downloader Server 1.0.0"
echo "========================================"
echo "URL: http://127.0.0.1:8080"
echo "Download directory:"
echo "/storage/emulated/0/Download/ALF Downloader"
echo

python server.py
