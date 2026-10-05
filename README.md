# ALF Launcher
Native Android home launcher (Kotlin, Jetpack Compose). Package `com.alfread.alflauncher`, minSdk 24, targetSdk 35.

## Features
Default-launcher intent filters (HOME), real installed apps via PackageManager, auto prune on uninstall (package broadcasts), swipe-up app drawer with live search, multi-page home with indicator, glass dock (4–8 slots, opacity, reorder), system wallpaper, live clock (12/24h), light/dark/system theme, local persistence (SharedPreferences).

## Build
GitHub: push, Actions builds `gradle assembleDebug`, APK is in the artifact.
Local: Android Studio, open folder, Run (JDK 17).

## Default launcher
Settings (gear, top right) → Set as default launcher, or press Home and choose ALF Launcher → Always.

## Use
Drawer: long-press an app → Add to Home / Add to Dock / Uninstall. Dock: long-press → move or remove.

## Limitations
Not yet implemented: folders, AppWidgetHost widgets, drag & drop, edit mode, import/export.
Backdrop blur of the wallpaper is not available to normal apps; the dock uses translucent gradient, border and shadow.
Gradle wrapper jar is not included (generate with `gradle wrapper`).
