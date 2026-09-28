# CI build notes

GitHub Actions uses:

- JDK 17
- Android SDK platform 35
- Build tools 35.0.0
- Gradle 8.9
- Android Gradle Plugin 8.7.3

The project intentionally does not run `sdkmanager "tools"`; only current SDK packages are installed.

Artifact:
`app/build/outputs/apk/debug/*.apk`
