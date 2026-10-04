# Lumira (Android) — Thin Client

> **Status note: 2026-10-04.** Android work is in progress. The Card/Course Space home and Card workspace now call Vision APIs; email/password sign-in uses Firebase Authentication. The existing PDF reader and Selection Engine remain available, with Card Resources uploaded and opened through the existing reader.

An Android app for Cards, Course Spaces, study materials, quizzes, flashcards,
Sarah, activity, and notifications, with the existing PDF reader and Selection
Engine. Built with Kotlin, Jetpack Compose, MVVM, Room, Retrofit, and Firebase
Authentication. Domain authorization and business rules remain server-side.

*Note: the underlying Gradle `namespace`/`applicationId` remain
`com.aipdfreader.app` for now — this was a display-name/branding rename
only (app name, launcher icon, in-app assistant persona), not a package
rename. Renaming the applicationId is a larger, separate change (it touches
the FileProvider authority, generated `BuildConfig`, and every import in the
codebase) and hasn't been done here.*

**This client holds no AI provider logic.** Firebase authenticates users and
provides Firebase ID tokens. The app sends those tokens to the Vision backend
and sends plain, descriptive requests ("the user selected this passage and
asked this question"). The backend owns provider selection,
prompt engineering, API keys, OCR, and context building. *(A prior version
of this README referenced an `ENGINEERING_REPORT.md` for further rationale
— that file does not exist anywhere in this repository; treat that as a
stale reference. `docs/engineering/` is the real, current engineering
record — see §6 below.)*

---

## 1. Requirements

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 17**
- **Android SDK 35** (compile/target), **minSdk 26**
- Access to a running instance of **our backend** (this repo does not
  include it — see `BACKEND_BASE_URL` below)

## 2. Opening the project

1. Unzip the project.
2. In Android Studio: **File → Open** → select the folder containing
   `settings.gradle.kts`.
3. Let Gradle sync (needs internet access to `google()`/`mavenCentral()`).
4. Point the app at your backend (see below).
5. Run on a device or emulator running **API 26+**.

## 3. Configuring the backend URL

The app knows about exactly one endpoint family: our own backend. There is
no in-app settings screen for this anymore (see the Engineering Report,
§2) — it's a build-time value:

- `app/build.gradle.kts` → `defaultConfig.buildConfigField("BACKEND_BASE_URL", …)`
  is the release default.
- The `debug` build type overrides it to `http://10.0.2.2:8080/`, the
  emulator's alias for your host machine's `localhost` — convenient for
  running the backend locally during development. A matching
  `network_security_config.xml` (debug-only) permits cleartext HTTP to that
  address; release builds remain HTTPS-only.

To point at a real backend, edit the `BACKEND_BASE_URL` value(s) and
rebuild. For multiple environments (dev/staging/prod), the standard next
step is Gradle product flavors — not implemented here to keep the diff
focused, but a natural extension.

## 4. Firebase setup and authentication

1. Register `com.aipdfreader.app` as an Android app in the Firebase project used by the backend, and enable **Email/Password** sign-in.
2. Download that app's `google-services.json` and place it in `frontend/app/google-services.json`.
3. Confirm the Firebase project ID matches `LUMIRA_FIREBASE_PROJECT_ID` on the backend. Never put backend Admin SDK credentials in Android.

The Firebase SDK owns the sign-in session and ID-token refresh lifecycle. `AuthInterceptor` attaches the current Firebase ID token as a Bearer token to Vision API requests; the app does not store a separate Vision token or log token contents.

**Account provisioning is deferred by AD-082.** Firebase sign-in can succeed before a trusted onboarding flow creates the matching Vision user. Until that mapping exists, protected API calls explain that this account is not linked. The app does not link identities by email.
## 5. Current navigation and PDF flow

The app opens to Firebase sign-in or the backend-backed Cards / Course Spaces home. From a Card workspace, the user can upload and open PDF Resources, create Notes and Study Sets, build/take Quizzes, review Flashcards, ask Sarah, see Course Space members and Updates, and view in-app notifications. PDFs downloaded from a Resource are added to the existing local Room library so the current Reader and Selection Engine can display them.

The standalone Library still supports local PDF import. Sarah requires a backend Resource because the local-only PDF has no Vision Resource ID. The Reader explains that constraint if Sarah is selected for a PDF imported only to the device.

## 6. Architecture

- Authentication: Firebase email/password; ID tokens attach to Vision requests.
- Cards/Course Spaces: CardApi, CardHomeViewModel, and Card workspace sharing/member UI.
- Study features: DomainApi and the workspace view model call resource, Note, Study Set, Quiz, Flashcard, Sarah, event, and notification endpoints.
- PDF: existing Room library, local renderer, text extractor, and Selection Engine; backend Resource bytes are imported into the same reader path.
- Networking: Retrofit, Kotlin serialization, OkHttp, and Hilt. The client contains no provider keys or authoritative authorization rules.

## 6.1 Firebase setup

Add the Firebase Console-generated google-services.json to frontend/app/ and enable Email/Password sign-in for Android package com.aipdfreader.app. Set the backend LUMIRA_FIREBASE_PROJECT_ID to that same Firebase project. The Android file contains project-specific public client identifiers; backend Admin credentials must remain on the server.

## 7. Known limitations / next steps

- **Firebase UID provisioning**: AD-082 defers the trusted process that
  maps authenticated Firebase users to Vision users. Protected app APIs
  remain unavailable until that mapping is made.
- **Android Studio validation**: the client has not yet been built or
  exercised on an emulator. Add google-services.json, sync/build, and debug
  before treating the Android implementation as complete.
- **Account deletion**: AD-082 requires successful Vision account deletion
  to also remove or disable its Firebase identity. That cross-system backend
  behavior must be confirmed before wiring account deletion in the client.
- **Scanned/image-only PDFs**: local extraction (PDFBox) has no OCR: the
  selection panel will show "No extractable text found" for scanned pages.
  Server-side OCR (already in the target architecture) is the right place
  to solve this for AI answers; wiring a page-image upload path for that is
  a recommended next milestone.
- **Selection Engine status**: see `docs/engineering/` for exactly what's
  implemented (foundation layer + text layout engine as of this writing)
  versus what's planned next (polygon–text intersection, OCR fallback).
