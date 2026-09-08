# Floating AI Orb v4.0

Perbaikan utama:
- Tombol Voice pada floating panel tidak lagi membuka MainActivity. Voice dijalankan dari service dan hasil transkripsi masuk kembali ke kolom input.
- Riwayat chat floating panel disimpan di SharedPreferences sehingga tidak hilang saat panel ditutup dan dibuka lagi.
- Draft teks yang belum dikirim juga dipertahankan.
- Tombol Screen dapat meminta izin MediaProjection melalui MainActivity hanya saat Screen Vision belum aktif.
- Snapshot layar ditampilkan sebagai preview di chat sebelum jawaban AI.
- Tombol cepat: Bersihkan, Salin, TikTok, Screen, Ringkas.
- Mic permission ditangani di MainActivity.
- Foreground service mendeklarasikan tipe microphone + mediaProjection.
- Android Actions menggunakan setup-java v5.

Catatan: Screen Vision tetap membutuhkan persetujuan sistem MediaProjection pada Android. Voice tetap membutuhkan izin RECORD_AUDIO. TikTok automation tetap membutuhkan Accessibility Service yang diaktifkan manual oleh pengguna.
