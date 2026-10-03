#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock
exec python server.py
