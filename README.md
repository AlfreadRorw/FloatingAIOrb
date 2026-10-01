# Alfread Status Downloader

Aplikasi Android (Kotlin + Jetpack Compose, tema hitam-putih) untuk mengunduh status WhatsApp.

- Sumber : `/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/`
          (+ WA Business & path lama Android 10 ke bawah)
- Tujuan : `/storage/emulated/0/Download/` (otomatis dipindai agar muncul di Galeri)

## Fitur
- Dock bar: **Status**, **Riwayat**, **Setting**
- Grid status foto/video dengan thumbnail, filter Semua/Foto/Video
- Pratinjau layar penuh (foto & video), bagikan, unduh
- Pilih banyak (tekan lama) lalu unduh sekaligus
- Riwayat unduhan + statistik, buka/bagikan/hapus
- Setting: tema (sistem/terang/gelap), kolom grid, urutan, WA Business, mode pindahkan, awalan nama file, kelola izin
- Header tidak menutupi status bar (sinyal & baterai)
- Tab **Terhapus**: pemulihan pesan WhatsApp yang dihapus pengirim (teks, foto, video, VN/audio, dokumen)
  - Teks: dibaca dari notifikasi WhatsApp (NotificationListenerService), ditandai DIHAPUS saat pengirim menghapus
  - Media: otomatis dicadangkan saat masuk, lalu dikaitkan ke pesannya
  - Pencarian, filter tipe, pemutar VN, simpan ke Download, retensi catatan (7/30/90 hari/selamanya)
  - Semua data tersimpan lokal di HP, tidak dikirim ke server mana pun

### Syarat agar Terhapus berfungsi
1. Aktifkan **Akses Notifikasi** untuk Alfread (Android 13+ untuk APK di luar Play Store: Info Aplikasi -> menu tiga titik -> *Izinkan setelan terbatas*, lalu ulangi).
2. Aktifkan **unduh otomatis media** di WhatsApp agar foto/video/VN/dokumen sudah ada di HP saat pesan masuk.
3. Matikan penghemat baterai untuk Alfread agar layanan notifikasi tidak dimatikan sistem.
4. Hanya pesan yang masuk SAAT pencatatan aktif yang bisa dipulihkan; pesan lama sebelum aplikasi dipasang tidak bisa.

## Build lewat GitHub Actions
1. Buat repo baru di GitHub, upload semua isi folder ini, push ke branch `main`.
2. Buka tab **Actions** -> workflow *Build Alfread Status Downloader* berjalan otomatis.
3. Selesai -> unduh APK di bagian **Artifacts** (`...-release` atau `...-debug`).
4. Rilis otomatis: `git tag v1.0.0 && git push origin v1.0.0` -> APK masuk ke GitHub Releases.

Tidak perlu `gradlew`; workflow memasang Gradle 8.9 sendiri.

### (Opsional) APK ber-signature sendiri
Isi Secrets repo: `KEYSTORE_BASE64` (hasil `base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Tanpa itu, APK release ditandatangani dengan debug key (tetap bisa dipasang).

## Izin
Android 11+ memakai **Akses semua file** (MANAGE_EXTERNAL_STORAGE) agar bisa membaca `Android/media/com.whatsapp`.
Android 10 ke bawah memakai izin Penyimpanan biasa.

Lihat `LOGO_GUIDE.md` untuk ukuran logo dan ikon.
