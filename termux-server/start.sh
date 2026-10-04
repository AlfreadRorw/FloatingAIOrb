#!/data/data/com.termux/files/usr/bin/bash
# ALF server — pengawas (supervisor): bila python mati/terbunuh, otomatis dinyalakan lagi.
# Keluar normal (kode 0, mis. tombol "Matikan" di aplikasi) = pengawas ikut berhenti.
cd "$(dirname "$0")" || exit 1
LOG="$HOME/.alf-server.log"
LOCK="$HOME/.alf-supervisor.pid"

ping_ok() { curl -fs --max-time 1 http://127.0.0.1:8080/api/ping >/dev/null 2>&1; }

# Cegah dua server / dua pengawas (ALF bisa mengirim perintah start lebih dari sekali)
if command -v curl >/dev/null 2>&1 && ping_ok; then
  echo "[start.sh] server sudah berjalan"; exit 0
fi
if [ -f "$LOCK" ] && kill -0 "$(cat "$LOCK" 2>/dev/null)" 2>/dev/null; then
  echo "[start.sh] pengawas sudah aktif, server sedang menyala"; exit 0
fi

# Jangan ikut mati saat sesi/jendela Termux ditutup
trap '' HUP PIPE
echo $$ > "$LOCK"
trap 'rm -f "$LOCK"' EXIT

# Wake-lock menjaga layanan Termux tetap hidup walau Termux ditutup / layar mati
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock
export PYTHONUNBUFFERED=1

# Pangkas log bila terlalu besar
if [ -f "$LOG" ] && [ "$(wc -c < "$LOG" 2>/dev/null || echo 0)" -gt 2000000 ]; then
  tail -n 500 "$LOG" > "$LOG.tmp" 2>/dev/null && mv -f "$LOG.tmp" "$LOG"
fi

fast=0
while true; do
  t0=$(date +%s)
  python server.py >> "$LOG" 2>&1 < /dev/null
  code=$?
  [ "$code" -eq 0 ] && break
  t1=$(date +%s)
  if [ $((t1 - t0)) -lt 10 ]; then fast=$((fast + 1)); else fast=0; fi
  if [ "$fast" -ge 8 ]; then
    echo "[start.sh] server crash beruntun, pengawas berhenti (cek log di atas)" >> "$LOG"
    break
  fi
  echo "[start.sh] server berhenti (kode $code), menyalakan ulang…" >> "$LOG"
  sleep 2
done
