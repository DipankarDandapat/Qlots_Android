# Qlots native Android + Python backend

Qlots is a personal-finance planning app with a native Android client written in Kotlin and Jetpack Compose, backed by the latest Python/FastAPI API from the attached project archive. The client is not a React Native wrapper or WebView: the app screens, forms, navigation, charts, date picker, secure session storage and PDF sharing are Android/Kotlin code.

## Project layout

- `android/` — standalone Android Studio/Gradle app. Kotlin sources are under `android/app/src/main/java/com/qlots/app/`.
- `backend/` — attached Python/FastAPI backend and regression tests.
- `BUILD_ANDROID_APK_GUIDE.md` — step-by-step emulator, APK and Play App Bundle build instructions.
- `RELEASE_CHECKLIST.md` — remaining pre-publication checks.

There is no `mobile/` folder, JavaScript application, Expo/EAS project, `package.json`, Node dependency tree, or `node_modules`. The ZIP also excludes the uploaded `.env`, databases, local virtual environments, Android build output and signing keys.

## App functionality

The Compose client includes account creation and sign-in; encrypted bearer-token storage in Android Keystore; dashboard net worth, asset/liability totals, recurring cash-flow summary, history sparkline, asset allocation chart and financial-health indicator; editable assets, debts, income and expenses; FD/RD principal, rates, compounding and dates; loan APR, EMI and tenure; saved daily history; assumption-based five-year scenarios; a calculation-backed helper; profile/password updates; record/account deletion; and PDF report generation with Android's share sheet.

All calculations and data persistence remain on the Python API. The helper is calculation-backed in this MVP, not an external AI adviser. Values and projections are estimates based on user-entered information.

## Run the Python backend locally

From `backend/`, create an environment and install its requirements:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

For a local development server, then run:

```bash
cd backend
APP_ENV=development \
DATABASE_URL=sqlite:///./qlots.db \
JWT_SECRET='local-only-change-this-value' \
.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

The backend loads `backend/.env` if present. Start from `backend/.env.example` when using a file. Never use the sample development settings in production: configure a unique, random `JWT_SECRET`, a durable database, HTTPS, backups, and a production environment. The API itself rejects its built-in JWT default in production.

Run the backend regression suite with:

```bash
cd backend
DATABASE_URL=sqlite:///./test_qlots.db APP_ENV=test .venv/bin/pytest -q
```

## Build and preview Android

Open `android/` in Android Studio or follow [the Android APK build guide](BUILD_ANDROID_APK_GUIDE.md). The app defaults to the API URL used by the latest attached client, `https://one.qlots.in`. To use a local API with an Android emulator, pass `-PqlotsApiBaseUrl=http://10.0.2.2:8000` to a **debug** build. The Android emulator uses `10.0.2.2` to reach the host computer; a physical phone should use a reachable LAN/server address instead.

The project targets Android 16 / API 36. Google Play's current policy requires API 36 or higher for new apps and updates submitted from 31 August 2026. This source project does not itself submit or publish an app.

## Important release status

The native debug APK builds and its package/signature/archive have been verified. The minified release App Bundle also compiles and passes release lint, but it is unsigned because no upload key was supplied; it is not ready to upload. Before publishing, configure and protect your upload keystore, build and test a signed App Bundle, verify the live API and privacy controls, prepare the Play listing and data-safety declarations, and run the remaining items in `RELEASE_CHECKLIST.md`. Never commit signing credentials or production secrets.

## Official Android references

- [Google Play target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)
- [Android Gradle Plugin 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Jetpack Compose compiler Gradle plugin setup](https://developer.android.com/develop/ui/compose/compiler)
