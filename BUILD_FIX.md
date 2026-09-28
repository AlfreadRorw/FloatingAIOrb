# FloatSpace CI Build Fix

The GitHub Actions workflow intentionally does **not** use
`android-actions/setup-android@v3`.

That action can invoke the obsolete SDK package name `tools`. Modern Android SDK
repositories no longer provide a package named `tools`, which causes:

    Warning: Failed to find package 'tools'

The workflow therefore uses the runner's preinstalled `sdkmanager` directly and
installs only:

- platform-tools
- platforms;android-35
- build-tools;35.0.0

The workflow also accepts SDK licenses non-interactively and builds with JDK 17
and Gradle 8.9.
