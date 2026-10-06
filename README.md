# ALF Vision Panel

Native Android floating AI screen assistant using Groq vision models.

## Features
- Floating AI bubble and panel above other apps.
- User-controlled screen region: move and resize a visible selection rectangle.
- Region screenshot via Android MediaProjection.
- Groq vision analysis using qwen/qwen3.8-27b or qwen/qwen3.6-27b.
- API key entered manually and encrypted locally with Android Keystore.
- Local prompt/system-instruction settings.
- Local history of questions and answers.
- No backend, Firebase, or proxy server.
- GitHub Actions debug APK build.

## First launch
1. Install APK.
2. Enter your Groq API key.
3. Save it.
4. Press Start Panel.
5. Grant screen-capture permission.
6. Grant "Display over other apps" permission if requested.
7. Tap the floating AI bubble.
8. Press Select to edit the capture area, then Ask or Analyze.

## Important
The app uses Android's MediaProjection consent flow. It does not silently read the screen. Shizuku is intentionally not required for the core workflow.
