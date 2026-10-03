# ALF Downloader 1.1

Aplikasi Android (Jetpack Compose) + server lokal Termux (Flask + yt-dlp) untuk mengunduh media dari sumber yang
didukung yt-dlp, khusus untuk konten yang memang boleh Anda unduh. Tidak melewati DRM, paywall, atau login.

## Perbaikan penting
- **Error "Cleartext HTTP traffic to 127.0.0.1 not permitted"** diperbaiki lewat `res/xml/network_security_config.xml`
  yang dipasang di `AndroidManifest.xml`.
- Peluncur server Termux memakai path absolut, mencari folder server otomatis, dan meminta izin RUN_COMMAND.
- Model data server memakai angka untuk kecepatan/ETA (sebelumnya bisa membuat parsing gagal).
- Server memakai `waitress` (tanpa peringatan "development server").

## Fitur
- UI gelap elegan, ikon di mana-mana, logo yang digambar langsung, 6 warna aksen
- **Dock bar** yang bisa diatur: gaya (melayang / menempel), label, ukuran ikon, kepekatan, lencana, getar
- Otomatis: nyalakan server, tempel link dari clipboard, ambil info, unduh dari menu Bagikan, kosongkan kolom, notifikasi
- Info video dengan sampul, durasi, tayangan; kualitas Terbaik/4K/1080/720/480/360/Audio (MP3/M4A/Opus)
- Sematkan sampul & metadata, subtitle, playlist, batas kecepatan, 1-4 unduhan bersamaan
- Antrean dengan kecepatan, ETA, progres playlist; batal / coba lagi
- Pustaka dengan pencarian, filter, unduh ulang, salin & bagikan link, buka folder
- Server menyimpan riwayat, memindai media agar muncul di Galeri/Musik

## Setup Termux
1. `termux-setup-storage`
2. Di folder `termux-server`: `bash install.sh` (juga mengaktifkan `allow-external-apps=true`)
3. Buka aplikasi dan izinkan "Run commands in Termux environment".
4. Bagikan link dari TikTok/YouTube ke ALF untuk unduh instan.

Build APK: GitHub Actions `Build ALF Downloader APK` (minSdk 26).
