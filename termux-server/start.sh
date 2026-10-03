#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
echo "ALF Downloader Server 1.1.0"
echo "Serving on http://127.0.0.1:8080"
echo "Download directory: /storage/emulated/0/Download/ALF Downloader"
exec python server.py --host 127.0.0.1 --port 8080
