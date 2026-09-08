# Floating AI Orb V9 - Action Smart Fix

Perbaikan utama V9:
- Memperbaiki error Kotlin `ACTION_IME_ENTER` memakai API yang benar: `AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id`.
- Fallback pencarian untuk Android lama atau aplikasi yang mengabaikan IME Enter dengan mencoba tombol Search/Cari/Done.
- Deteksi aplikasi launcher dari perangkat untuk perintah `buka <nama aplikasi>`.
- Perintah `daftar aplikasi` menampilkan aplikasi launcher yang terdeteksi.
- Perintah `cari aplikasi <nama>` mencari aplikasi berdasarkan nama dan langsung membukanya jika ditemukan.
- Perintah TikTok tetap mendukung pencarian, komentar, dan menyiapkan balasan chat dengan konfirmasi sebelum tindakan kirim.
- Tema dan pengaturan dari versi sebelumnya tetap dipertahankan.

Contoh perintah:
- buka YouTube
- buka Kalkulator
- daftar aplikasi
- cari aplikasi CapCut
- cari di TikTok kucing lucu
- balas chat di TikTok dari Yama balas nanti aku kabarin

Catatan:
Automasi UI aplikasi pihak ketiga melalui Accessibility dapat berubah jika aplikasi target mengubah struktur tampilannya. Tindakan pengiriman pesan/komentar tetap memerlukan konfirmasi pengguna.
