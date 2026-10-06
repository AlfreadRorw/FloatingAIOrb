# Auto Trigger 2.0

## Upgrade utama
- Live macro speed dari 0.10x sampai 3.00x.
- Perubahan speed berlaku saat macro sedang berjalan.
- Speed sekarang memengaruhi delay, durasi tap/tahan, dan durasi swipe sehingga mode cepat tidak lagi terasa tertahan oleh durasi rekaman asli.
- Quick control panel untuk Lanjut, Jeda semua, Stop semua, Play terakhir, Target tap, dan Rekam baru.
- Preset Mode ML dan Normal langsung dari floating panel.
- Ikon setiap macro bisa dipilih dari beberapa ikon bawaan dan pilihan tersimpan saat aplikasi ditutup.
- Ikon floating macro menampilkan ikon pilihan saat idle dan play/pause saat aktif.
- Panel diperbesar dan dibuat lebih terstruktur agar kontrol utama mudah dijangkau.
- Macro lama tetap kompatibel karena field ikon memiliki nilai default otomatis.

## Catatan
Build lokal tidak dijalankan di environment ini karena Gradle distribution tidak tersedia tanpa akses jaringan. Source sudah diperiksa untuk struktur Kotlin, referensi resource, dan delimiter balance; build akhir tetap perlu dijalankan oleh GitHub Actions.
