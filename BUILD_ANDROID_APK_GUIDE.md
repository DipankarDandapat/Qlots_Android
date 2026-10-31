# Build and test the Qlots Android app

This guide is for the native Kotlin project in `android/`. It does not use Node.js, npm, Expo or React Native.

## 1. Open the Android project

Install Android Studio with a JDK 17-compatible setup and Android SDK Platform 36. In Android Studio, choose **Open** and select the `android/` folder (the folder containing `settings.gradle.kts`). Allow Gradle sync to finish. The project includes the Gradle 8.13 wrapper, so use `./gradlew` rather than installing Gradle globally.

Alternatively, from a terminal in `android/`, confirm Java is available and download the Gradle distribution through the wrapper:

```bash
java -version
./gradlew --version
```

If Android Studio prompts you to install Android SDK Platform 36 or build tools, accept the prompt and wait for installation. The project uses Android API 36 for both compile and target levels.

## 2. Start the Python API for local testing (optional)

The app's default API is `https://one.qlots.in`. To use the backend source in this project instead, open a second terminal:

```bash
cd backend
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
APP_ENV=development \
DATABASE_URL=sqlite:///./qlots.db \
JWT_SECRET='local-development-key-change-me' \
.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

The API should respond at `http://127.0.0.1:8000/health` when checked from the same computer.

## 3. Build a debug APK for the Android emulator

In the emulator, `10.0.2.2` is the special address that reaches the host computer. From `android/`, build the APK with the local backend address:

```bash
./gradlew assembleDebug -PqlotsApiBaseUrl=http://10.0.2.2:8000
```

The APK appears at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

In Android Studio, start a virtual device and press **Run**. Or install the APK into a connected emulator with:

```bash
./gradlew installDebug -PqlotsApiBaseUrl=http://10.0.2.2:8000
```

The debug app permits cleartext HTTP so it can reach a local development API. Release builds prohibit cleartext traffic; use an HTTPS API for any release build.

## 4. Test on a physical Android phone without changing the API code

Connect the phone and development computer to the same trusted Wi-Fi. Start the Python backend bound to `0.0.0.0`, find the computer's LAN IP address (for example, `192.168.1.25`), and allow inbound port 8000 through the computer firewall only on that private network. Build a debug APK using that address:

```bash
./gradlew assembleDebug -PqlotsApiBaseUrl=http://192.168.1.25:8000
```

Transfer `app-debug.apk` to the phone and install it, or run `./gradlew installDebug` with USB debugging enabled. Do not use a public Wi-Fi network or expose this development server to the internet. For a stable test, use an HTTPS test server and set `-PqlotsApiBaseUrl=https://your-test-api.example`.

## 5. Where to find and install the APK

The Android Studio **Build APK(s)** action and `assembleDebug` both write an APK under `android/app/build/outputs/apk/debug/`. To install with Android Debug Bridge, if it is installed:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is for development/testing. It is not the signed release App Bundle Google Play expects for a new store listing.

## 6. Build a signed App Bundle for Google Play

Create an upload key once and store it somewhere backed up and access-controlled. Do not add the keystore or its passwords to this project or source control. Example command:

```bash
keytool -genkeypair -v \
  -keystore /secure/location/qlots-upload.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias qlots-upload
```

Set the four signing environment variables in the shell that will run Gradle:

```bash
export QLOTS_UPLOAD_STORE_FILE=/secure/location/qlots-upload.jks
export QLOTS_UPLOAD_STORE_PASSWORD='your-keystore-password'
export QLOTS_UPLOAD_KEY_ALIAS='qlots-upload'
export QLOTS_UPLOAD_KEY_PASSWORD='your-key-password'
```

Then build the release App Bundle using the production HTTPS API (or omit this property to use the default `https://one.qlots.in`):

```bash
./gradlew bundleRelease -PqlotsApiBaseUrl=https://your-production-api.example
```

The resulting file is `android/app/build/outputs/bundle/release/app-release.aab`. Inspect and test the signed build before uploading it through your own Google Play Console account. This project does not upload the bundle or create a Play listing.

## 7. Verify the backend

Run the Python API tests from `backend/`:

```bash
DATABASE_URL=sqlite:///./test_qlots.db APP_ENV=test .venv/bin/pytest -q
```

The suite covers registration/login, account data isolation, entry creation/deletion, dashboard and projection calculations, and PDF report generation.

## Current Play Store target

This project compiles and targets API 36. Google's current guidance says new apps and updates must target Android 16 (API 36) or higher for Google Play submissions from 31 August 2026. Check Google's policy again before a future submission:

- [Target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)
- [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
