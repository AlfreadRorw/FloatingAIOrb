# Floating AI Orb 3.0

Upgrade besar untuk Android phone-first:

- Responsive portrait layout untuk HP kecil maupun besar.
- Floating Orb yang bisa digeser, ketuk untuk membuka panel, panel dapat ditutup/minimize.
- Panel overlay dengan drag header, chat, screen vision, dan tombol Voice yang membuka voice mode.
- Voice chat memakai Android SpeechRecognizer + Text-to-Speech.
- Preset suara Kawaii, Cute, Cool, dan Cyber melalui pitch/speech-rate TTS Android.
- Kamera full-resolution melalui FileProvider, bukan preview kecil.
- Screen capture via MediaProjection foreground service.
- Chat output dibersihkan dari <think>, reasoning, heading markdown, dan code fence.
- Quick prompts, status voice, animasi glow, dan UI yang lebih padat untuk layar HP.
- API key tetap di SharedPreferences perangkat, tidak ditanam di source.

## Build

GitHub Actions memakai Java 17 dan Gradle 8.9.

Workflow: `.github/workflows/android.yml`

## Setup

Buka aplikasi → SETUP → masukkan API key, model, dan endpoint. Untuk vision, pilih model vision yang tersedia pada provider kamu.

## Voice

Berikan izin microphone saat diminta. Suara anime-style adalah preset pitch/rate TTS Android; pilihan suara aktual tetap mengikuti engine TTS yang terpasang di HP.

## V4 AI Action Assist
- Voice/text commands: "Buka TikTok", "Buka WhatsApp", etc.
- TikTok assist command example: "Buka TikTok, buka komentar, tulis halo semuanya".
- The Accessibility Service must be enabled manually in Android Settings.
- The service may navigate and fill text, but the final public Send action always opens an explicit confirmation screen.
- No mass-commenting or background spam loop is included.
