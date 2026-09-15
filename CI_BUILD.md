# GitHub Actions build setup

This project is prepared for a reproducible Android debug build in GitHub Actions.

## Build environment
- Ubuntu latest runner
- Temurin JDK 17
- Android SDK platform 35
- Android Build Tools 35.0.0
- Gradle wrapper 8.7
- Android SDK licenses accepted non-interactively
- `./gradlew clean assembleDebug --stacktrace`

## Important
`local.properties` is intentionally not included because its `sdk.dir` is machine-specific and should not be committed.

## Artifact
After a successful workflow, download `android-debug-apk` from the Actions run.

## If the next build fails
The workflow deliberately uses `--stacktrace` and `--warning-mode all`, so the first real `FAILURE`, `Caused by`, or `e:` compiler error is the useful part of the log. SDK license text and deprecation warnings alone are not necessarily build failures.
