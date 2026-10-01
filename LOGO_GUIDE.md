# Panduan Logo & Aset (ganti file placeholder dengan punyamu)

Semua file ada di: `app/src/main/res/drawable-nodpi/`
**Nama file harus sama persis**, format PNG transparan.

| File | Ukuran | Dipakai untuk | Catatan |
|------|--------|---------------|---------|
| `ic_launcher_foreground.png` | **432 x 432 px** | Ikon aplikasi (launcher) | Latar transparan. Taruh logo di tengah, area aman **264 x 264 px** (lingkaran tengah), sisanya bisa terpotong oleh bentuk ikon HP. Latar ikon hitam diatur di `values/colors.xml` (`ic_launcher_background`). |
| `logo_app.png` | **512 x 512 px** | Logo di header & halaman Tentang | Boleh punya background sendiri (persegi, sudut dibulatkan otomatis). |
| `ic_nav_status.png` | **96 x 96 px** | Ikon dock: Status | Satu warna, latar transparan. Warna apa pun, nanti otomatis jadi hitam/putih. Sisakan margin ~8 px. |
| `ic_nav_history.png` | **96 x 96 px** | Ikon dock: Riwayat | Sama seperti di atas. |
| `ic_nav_settings.png` | **96 x 96 px** | Ikon dock: Setting | Sama seperti di atas. |

Tips:
- Ikon dock harus siluet (bentuk solid) bertransparansi. Jangan pakai gradasi/banyak warna.
- Mau ganti warna latar ikon aplikasi? Ubah `ic_launcher_background` di `values/colors.xml`.
- Setelah mengganti file, commit & push; GitHub Actions akan membangun ulang APK.
