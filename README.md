# AWNISH Calculator

A privacy-first Android calculator with an explicitly labelled, locally encrypted Private Gallery. See the in-app Privacy screen and the setup notes below.

## Build

Open this folder in Android Studio (Ladybug or newer), allow it to install the Android SDK 35, then run the `app` configuration. From a terminal with Android SDK/Gradle installed: `gradle :app:assembleDebug`.

## Run on a phone

Enable Developer options and USB debugging, connect the phone, select it in Android Studio, and press Run. The gallery uses the system Photo Picker, so it does not require broad media permissions.

## Security model

Imported files are copied into `filesDir/private_gallery/`, which is app-private storage. Bytes are encrypted with AES/GCM before writing. The AES key is generated and retained by Android Keystore under `awnish_gallery_key`; it is never persisted as plaintext by the app. The gallery requires BiometricPrompt or device credential authentication when lock is enabled, locks on backgrounding, and exports only after an explicit user action. No network permission, accounts, analytics, Firebase, or cloud service is present.

## Customization and release

Change the visible name in `app/src/main/res/values/strings.xml`; adjust colors in `ui/theme/Theme.kt`; replace adaptive launcher icons in `mipmap-anydpi-v26`. Build a release APK with `gradle :app:assembleRelease` after configuring a signing key in `app/build.gradle.kts` (or Android Studio's Generate Signed Bundle/APK flow).
