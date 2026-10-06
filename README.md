# ALF Vision Panel

Native Android floating AI vision panel.

## Features

- Floating panel above other apps.
- User-defined movable/resizable screen capture rectangle.
- Android MediaProjection screen capture.
- Groq Qwen 3.8 27B vision model.
- Manual Groq API key.
- API key protected with Android Keystore AES-GCM.
- Local system prompt and model settings.
- No Firebase.
- No backend.
- No Shizuku required.
- GitHub Actions build.

## Build

GitHub Actions builds the debug APK automatically.

## Usage

1. Install APK.
2. Enter Groq API key.
3. Grant overlay permission.
4. Start ALF Vision.
5. Accept Android screen capture permission.
6. Adjust the selection rectangle.
7. Open the ALF floating panel.
8. Ask a question and press Analisis Layar.

Groq vision endpoint:
https://api.groq.com/openai/v1/chat/completions

Vision model:
qwen/qwen3.8-27b

When the selection is finished, press Kunci in the floating panel so the selection overlay disappears and the underlying app can be used normally.
