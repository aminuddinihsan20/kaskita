# KasKita

Aplikasi Android pencatat arus kas pribadi (Kotlin, Jetpack Compose, Room). Data tersimpan di perangkat, semua fitur inti
bekerja tanpa internet. Fitur opsional: analisis rekap bulanan dengan Gemini lewat Firebase AI Logic.

## Build

Syarat: Android Studio (JDK 17+), Android SDK 36.

```
./gradlew testDebugUnitTest assembleDebug
```

Berkas yang **tidak** ada di repositori ini dan harus Anda sediakan sendiri:

- `app/google-services.json` dari project Firebase Anda (aplikasi `com.kaskita.lokal`, Firebase AI Logic dengan Gemini
  Developer API aktif). Tanpa berkas ini build gagal.
- `local.properties`: `sdk.dir=...` dan, opsional, `appcheck.debugToken=<UUID>` (token debug App Check yang didaftarkan
  di Firebase console; tanpa ini aplikasi membuat token acak per pemasangan, lihat "Lihat kode pendaftaran" di aplikasi).
- `signing/keystore.properties` dan `signing/kaskita-release.jks` untuk build rilis bertanda tangan
  (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).

Jangan unggah APK yang dibangun dengan `appcheck.debugToken`: tokennya ikut tertanam di dalamnya.
