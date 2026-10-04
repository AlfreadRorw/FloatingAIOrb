# ALF Downloader 1.1

Aplikasi Android (Jetpack Compose) + server lokal Termux (Flask + yt-dlp) untuk mengunduh media dari sumber yang
didukung yt-dlp, khusus untuk konten yang memang boleh Anda unduh. Tidak melewati DRM, paywall, atau login.

## Perbaikan penting
- **Error "Cleartext HTTP traffic to 127.0.0.1 not permitted"** diperbaiki lewat `res/xml/network_security_config.xml`
  yang dipasang di `AndroidManifest.xml`.
- Peluncur server Termux memakai path absolut, mencari folder server otomatis, dan meminta izin RUN_COMMAND.
- Model data server memakai angka untuk kecepatan/ETA (sebelumnya bisa membuat parsing gagal).
- Server memakai `waitress` (tanpa peringatan "development server").

## Baru di 1.2
- Bar mengambang kini berbentuk garis tegak putih di tepi layar (bisa kiri/kanan), diseret naik-turun, ketuk untuk buka panel.
- Panel bar: tempel+unduh cepat, progres unduhan, dan pintasan "Jendela kecil" (mini browser mengambang untuk TikTok/WhatsApp/YouTube/Instagram — bisa diseret & diubah ukurannya).
- Integrasi Shizuku (opsional): membebaskan Termux & ALF dari pembatasan baterai, memberi izin overlay tanpa ke Pengaturan, dan percobaan membuka app asli dalam mode jendela bebas (freeform) — hasil freeform tergantung dukungan perangkat.
- Widget layar utama (bisa diubah ukuran) + ubin Pengaturan Cepat untuk menyalakan server.
- Tombol "Perbarui yt-dlp" di Pengaturan untuk mengatasi error "Unsupported URL" (biasanya postingan foto/slide TikTok yang butuh yt-dlp lebih baru).

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

## Native Freeform Windows (ALF v1.3 changes)

The floating app panel no longer uses a WebView for TikTok, WhatsApp, YouTube, Instagram, or other apps. It enumerates real installed launcher apps and asks Android to open the selected package as a real freeform task when Shizuku is available.

Setup on a supported device:

1. Start Shizuku and grant ALF permission.
2. Give ALF the "display over other apps" permission.
3. In ALF → Settings → Shizuku, use **Aktifkan mode freeform** once.
4. Turn on the floating bar.
5. Open the bar, search/select an installed app, and tap it.
6. Use the width/height sliders for the next native window. While a native window is open, horizontal dragging on the edge bar also changes its width.

The panel uses `NOT_TOUCH_MODAL` so touches outside the panel are passed to the app underneath instead of freezing the screen.

Freeform is controlled by Android's window manager, so support depends on the phone's ROM/build. On devices that reject freeform, ALF falls back to opening the real app normally instead of showing a WebView.

## v1.4 — Perbaikan Shizuku & jendela mengambang
- **Shizuku tidak mendeteksi ALF**: ditambah `ShizukuProvider` + izin `moe.shizuku.manager.permission.API_V23` + dependency `shizuku:provider`. Tanpa ini ALF tidak muncul di "Aplikasi terotorisasi".
- Status Shizuku kini reaktif (listener binder), tombol "Buka Shizuku", deteksi izin yang ditolak permanen.
- Peluncuran freeform diverifikasi (cek windowing mode task), dipaksa freeform bila terbuka fullscreen, dan memberi pesan alasan bila gagal.
- Tanpa Shizuku: tetap mencoba jendela kecil lewat launch bounds.
- Baru: panel menciut otomatis setelah buka app, tempel link clipboard otomatis, favorit ★ (tahan ikon), preset ukuran, 5 posisi jendela, halaman Diagnosa.

## v1.5 — Tema panel, server Termux cepat & otomatis
- **Server jauh lebih cepat siap**: yt-dlp dimuat di thread latar (server langsung merespons), `/api/ping`, `/api/log`, `start.sh` mencegah server ganda.
- **Termux otomatis**: `startForegroundService` (wajib di Android 8+), percobaan ulang otomatis, tahap progres ("Menjalankan Termux…", "Memuat yt-dlp…"), watchdog di bar mengambang yang menyalakan ulang server bila mati, jalankan setelah HP menyala.
- **Pasang server otomatis dari APK** (server.py + start.sh ada di assets), tombol "Salin izin" (allow-external-apps), dan pemberian izin Run-Command lewat Shizuku.
- **7 tema panel**: Obsidian, Kaca, Neon, Sunset, Aurora, Sakura (terang), AMOLED + kepekatan, sudut, lebar, blur, bar berdenyut/redup.
- **Panel 3 tab**: Unduh (pilih kualitas, batal, progres + kecepatan), Aplikasi (favorit ★, jendela freeform), Alat (status server, nyalakan/matikan, update yt-dlp, ganti tema langsung).
- Notifikasi menampilkan status server dengan tombol "Tutup bar".
