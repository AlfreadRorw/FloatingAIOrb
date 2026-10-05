# ALF Launcher

ALF Launcher adalah custom Android Home Launcher native berbasis Kotlin + Jetpack Compose. Target visualnya premium, minimal, dan glass-inspired, sementara fungsi inti menggunakan API Android resmi.

## Fitur

- Bisa dipilih sebagai default Home Launcher.
- Membaca aplikasi terinstall secara dinamis melalui PackageManager.
- Membuka aplikasi asli.
- App Drawer dengan pencarian real-time.
- Grid home yang dapat dikustomisasi.
- Dock floating bergaya glass.
- Deteksi install/update/uninstall package.
- Pengaturan lokal dengan DataStore.
- Dukungan light/dark/system.
- Jam real-time dan format 12/24 jam.
- Android AppWidget picker menggunakan AppWidgetManager/AppWidgetHost.
- UI fallback aman untuk perangkat tanpa dukungan blur tertentu.

## Build

### Android Studio
Buka folder project ini sebagai project Gradle.

### GitHub Actions
Push project ke GitHub. Workflow `.github/workflows/build.yml` akan:
1. Checkout.
2. Menyiapkan JDK 17.
3. Menyiapkan Android SDK.
4. Menginstal platform-tools, Android 35 dan build-tools 35.0.0.
5. Menjalankan `./gradlew assembleDebug --no-daemon`.
6. Mengunggah APK sebagai artifact.

## Install

Unduh artifact APK debug dari GitHub Actions lalu install di perangkat Android.

## Menjadikan default launcher

Buka ALF Launcher > Settings > Set as default launcher. Android akan membuka halaman Home/Default Apps resmi. Pilih ALF Launcher.

ALF Launcher tidak mencoba mengganti launcher secara paksa.

## App Drawer

Swipe ke atas pada home screen untuk membuka drawer. Gunakan kolom pencarian untuk memfilter aplikasi secara real-time.

## Edit Mode

Tekan lama ikon untuk masuk ke mode edit. Tombol Widgets membuka picker widget Android dan Settings membuka pengaturan launcher.

## Widget

ALF Launcher menggunakan AppWidgetManager/AppWidgetHost untuk meminta widget Android. Ketersediaan dan kemampuan resize widget mengikuti metadata provider masing-masing aplikasi dan kebijakan versi Android.

## Limitasi Android

Android tidak menyediakan API publik yang mengizinkan launcher pihak ketiga mengganti default launcher secara paksa. Sistem juga mengontrol konfigurasi tertentu untuk widget, gesture sistem, notification shade, dan wallpaper.

Efek blur penuh tidak tersedia dengan API yang sama di semua versi Android. Karena itu UI menggunakan glass-inspired translucent surfaces dan fallback visual agar tetap stabil pada Android lama.

## Struktur

- `launcher/` home screen dan state.
- `apps/` discovery dan launch aplikasi.
- `widgets/` AppWidget picker.
- `settings/` konfigurasi launcher.
- `ui/` glass UI dan theme.
- `data/` DataStore lokal.

## Catatan

Project ini sengaja tidak menggunakan Firebase, backend, server, root, Shizuku, Accessibility Service, VPN, atau overlay permission.


## Native implementation notes

The launcher activity is a real `CATEGORY_HOME` activity. Apps are discovered through `PackageManager` rather than a hardcoded list.

The widget flow uses `AppWidgetHost`, `AppWidgetManager.ACTION_APPWIDGET_PICK`, provider metadata, and provider configuration activities. Android controls the provider picker and configuration UI.

Android does not expose a public API for a third-party launcher to silently become the default launcher, so ALF Launcher opens the system Home settings instead.

The project intentionally avoids accessibility, root, Shizuku, overlay, VPN, Firebase, and background services.
