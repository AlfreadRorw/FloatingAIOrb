# ARACHNE CI

The workflow explicitly locates `sdkmanager` because GitHub-hosted Ubuntu runners may keep it in a versioned directory such as:

`$ANDROID_HOME/cmdline-tools/16.0/bin/sdkmanager`

The previous `sdkmanager: command not found` error happened because that directory was not on PATH.

The workflow installs Android platform 35 and build-tools 35.0.0, then builds `assembleDebug`.
