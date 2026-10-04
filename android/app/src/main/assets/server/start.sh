#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
# Cegah dua server berjalan sekaligus (ALF bisa mengirim perintah start lebih dari sekali)
if command -v curl >/dev/null 2>&1 && curl -fs --max-time 1 http://127.0.0.1:8080/api/ping >/dev/null 2>&1; then
  echo "[start.sh] server sudah berjalan"; exit 0
fi
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock
export PYTHONUNBUFFERED=1
exec python server.py
