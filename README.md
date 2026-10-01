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
