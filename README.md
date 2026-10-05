# ALF Voice Control

Aplikasi Android (Kotlin + Jetpack Compose) untuk mengontrol HP dengan suara yang
direkam sendiri oleh pengguna: menyalakan layar, mematikan/mengunci layar, dan
membuka aplikasi tertentu, dipicu oleh wake word + trigger phrase kustom.

Semua data (command, rekaman suara, setting, PIN) disimpan **lokal** di perangkat.
Tidak ada backend/server, tidak ada Firebase, tidak butuh internet untuk fitur
voice command setelah aplikasi berjalan.

---

## 1. Teknologi

- Kotlin, Jetpack Compose, Material 3
- Min SDK 24, Target SDK 35, JDK 17
- Room (penyimpanan command), DataStore (settings), EncryptedSharedPreferences (PIN)
- `SpeechRecognizer` bawaan Android untuk speech-to-text
- `MediaRecorder` untuk rekam voice sample lokal
- `DevicePolicyManager` (Device Administrator) untuk aksi screen-off
- GitHub Actions untuk build APK otomatis

---

## 2. Build APK via GitHub Actions (cara termudah)

1. Upload seluruh folder project ini ke repository GitHub baru.
2. GitHub Actions akan otomatis jalan (lihat `.github/workflows/build.yml`) setiap
   push ke `main`/`master`, atau jalankan manual lewat tab **Actions > Run workflow**.
3. Workflow ini:
   - Setup JDK 17 dan Android SDK (platform 35, build-tools 35.0.0)
   - **Meregenerasi `gradle-wrapper.jar`** otomatis via `gradle wrapper` (jar biner
     tidak disertakan langsung di repo karena bukan file teks, tapi akan dibuat ulang
     setiap build CI, jadi build selalu bisa jalan dari fresh clone)
   - Menjalankan `./gradlew assembleDebug`
   - Mengupload hasil APK sebagai artifact bernama `alf-voice-control-debug-apk`
4. Download APK dari halaman run workflow tersebut, di bagian **Artifacts**.

## 3. Build lokal (opsional, kalau punya Android Studio)

```
git clone <repo-anda>
cd ALFVoiceControl
./gradlew assembleDebug
```

Kalau `gradlew` gagal karena `gradle-wrapper.jar` belum ada (karena file biner ini
tidak ikut dibuat secara otomatis saat aplikasi ini dibuat), jalankan sekali:

```
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

(butuh Gradle terinstall di komputer Anda sekali saja, setelah itu `./gradlew`
akan berfungsi normal seterusnya untuk project ini).

APK hasil build ada di: `app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Instalasi APK ke HP

1. Salin file `.apk` ke HP Android Anda.
2. Buka file tersebut di File Manager, izinkan "Install dari sumber tidak dikenal"
   kalau diminta.
3. Install seperti biasa.

---

## 5. Setup Permission (saat pertama kali buka app)

Aplikasi akan memandu lewat onboarding, meminta permission satu per satu:

1. **Microphone** (`RECORD_AUDIO`) - wajib, untuk wake word & command.
2. **Notifications** (`POST_NOTIFICATIONS`, Android 13+) - supaya notifikasi
   "ALF is listening" muncul wajar sesuai kebijakan Android.
3. **Device Administrator** (opsional, hanya kalau mau pakai command "Screen Off") -
   diaktifkan manual di halaman Settings > Device Administrator.
4. **Battery optimization** (opsional) - supaya listening service tidak dimatikan
   paksa oleh OEM (terutama HP seperti Infinix/Xiaomi yang agresif membatasi
   background service).

Semua permission diminta **saat fitur terkait pertama kali dipakai**, bukan
sekaligus di awal.

---

## 6. Batasan penting Android (dibaca dulu sebelum pakai)

- **ALF tidak pernah membobol lock screen Android.** Command "Screen On" hanya
  membangunkan layar (via window flags resmi `FLAG_TURN_SCREEN_ON` /
  `setTurnScreenOn`); kalau HP terkunci dengan PIN/pola/password/biometrik,
  lock screen Android tetap muncul dan pengguna tetap harus autentikasi normal.
- **Command "Screen Off" memakai `DevicePolicyManager.lockNow()`**, API resmi
  Android untuk Device Administrator - bukan root, bukan exploit, bukan hidden API.
- **ALF PIN adalah PIN milik aplikasi ini saja**, dipakai untuk melindungi
  halaman Settings ALF. Ini **bukan pengganti** PIN/pola/password lock screen
  Android, dan disimpan dalam bentuk hash SHA-256 + salt lewat
  `EncryptedSharedPreferences` - tidak pernah plaintext.
- **"Open App" tidak hardcode aplikasi tertentu.** Daftar aplikasi diambil
  langsung dari `PackageManager` perangkat Anda, jadi command bisa dibuat untuk
  aplikasi apa pun yang sudah terinstall.
- **Voice matching di ALF adalah speech-to-text + fuzzy text matching**, BUKAN
  speaker verification biometrik. Artinya ALF mencocokkan *kalimat* yang
  diucapkan, bukan memverifikasi identitas suara Anda secara biometrik. Ini
  dijelaskan juga di dalam UI aplikasi.
- Beberapa versi/vendor Android bisa membatasi aplikasi membangunkan layar atau
  menjalankan activity dari background dalam kondisi tertentu. Kalau itu terjadi,
  ALF menampilkan status/error yang jujur - tidak berpura-pura berhasil.
- Rekaman suara dan semua data command disimpan di penyimpanan internal privat
  aplikasi (`filesDir/voice_samples`) dan **tidak pernah diunggah ke internet**.

---

## 7. Struktur Project

```
ALFVoiceControl/
├── .github/workflows/build.yml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/alfread/alfvoicecontrol/
│       │   ├── MainActivity.kt
│       │   ├── AlfApplication.kt
│       │   ├── AppContainer.kt
│       │   ├── data/           (Room, DataStore, installed apps lookup)
│       │   ├── voice/          (speech recognition, matcher, recorder, wake word loop)
│       │   ├── service/        (foreground listening service, boot receiver)
│       │   ├── screen/         (screen on/off controller + activity)
│       │   ├── security/       (device admin receiver, PIN manager)
│       │   ├── commands/       (command executor)
│       │   └── ui/             (Compose screens: onboarding, home, commands, settings, pin, device admin)
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── gradle/wrapper/gradle-wrapper.properties
```

Package: `com.alfread.alfvoicecontrol`
