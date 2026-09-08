# Floating AI Orb 2.0

Versi ini memperbaiki crash foreground-service MediaProjection dan membuat orb dapat muncul tanpa harus menyalakan screen capture terlebih dahulu.

## Fitur
- Floating orb dengan glow/pulse animation dan drag.
- Overlay chat AI yang bisa menerima input teks dan respons dari Groq/OpenAI-compatible endpoint.
- Screen capture Android dengan izin sistem, lalu tombol `Lihat layar` untuk mengirim frame terakhir ke AI vision.
- Kamera dari aplikasi melalui Android Camera intent; foto dapat dilampirkan ke pertanyaan AI.
- API key, endpoint, dan model dapat diubah dari aplikasi, bukan ditanam permanen di source.
- Ikon/logo Floating AI Orb.
- GitHub Actions memakai Java 17 + Gradle 8.9.

## Konfigurasi AI
Default endpoint: `https://api.groq.com/openai/v1/chat/completions`
Default model: `qwen/qwen3.6-27b`

Masukkan API key sendiri pada menu `SETUP`. Jangan commit API key ke GitHub.

## Build
Workflow: `.github/workflows/android.yml`.

Build lokal: `gradle --no-daemon clean assembleDebug` dengan JDK 17 dan Gradle 8.9.
