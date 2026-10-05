# Vision (Android) — Thin Client

> **Status note: 2026-10-04.** Android work is in progress. The Card/Course Space home and Card workspace now call Vision APIs; email/password sign-in uses Firebase Authentication. The existing PDF reader and Selection Engine remain available, with Card Resources uploaded and opened through the existing reader.

An Android app for Cards, Course Spaces, study materials, quizzes, flashcards,
Sarah, activity, and notifications, with an on-device file shelf, PDF reader,
and Selection Engine. Built with Kotlin, Jetpack Compose, MVVM, Room, Retrofit, and Firebase
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
- A running backend is needed for Course Spaces and AI features. Personal
  Cards, local files, reading, and Notes work offline.

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

The app opens to Firebase sign-in or the backend-backed Cards / Course Spaces home. From a Card workspace, the user can upload supported PDFs, Office documents, text files, and images; create Notes and Study Sets; build/take Quizzes; review Flashcards; ask Sarah; see Course Space members and Updates; and view in-app notifications. The **On this phone** shelf stores picked files in app-private storage before they are added to a Card. PDFs open in the app's Reader; other formats open in a compatible phone app. Supported files uploaded to a Card are processed by the backend, including OCR for images and scanned pages.

New accounts continue through a short animated learner setup (name, learning goal, interests, experience, and daily study target) with an optional profile photo. Those profile details are stored locally per Firebase UID for now; they do not create or provision a backend Vision account.

Personal Cards are saved locally first, so creating a Card, adding files, reading them, and writing Notes work without internet. When a real API endpoint is configured and reachable, Android's persistent network job syncs local Cards, materials, and Notes with the same signed-in user's private Vision Card and mirrors server Resources/Notes back to the phone. Offline edits remain on-device until sync succeeds. Course Spaces and Sarah's generation still require the backend. The checked-in release URL is an example placeholder because the production API is not deployed yet; Firebase UID provisioning is also still required before protected API calls can succeed. Existing online Cards retain the **Make shared** action to become Course Spaces.

Cards and Course Spaces can be searched from the home screen. Local file shelves and offline Cards show page previews for PDFs and image files, and document-type previews for other formats. The three-dot menu includes Account, Share Vision, Send feedback, and Refresh. Feedback currently opens Android's share sheet; it is not submitted to a Vision service.

Sarah requires a backend Resource because a file kept only in **On this phone** has no Vision Resource ID. Add a supported file to a Card to make it available to Sarah.

## 6. Architecture

- Authentication: Firebase email/password; ID tokens attach to Vision requests.
- Cards/Course Spaces: CardApi, CardHomeViewModel, and Card workspace sharing/member UI.
- Study features: DomainApi and the workspace view model call resource, Note, Study Set, Quiz, Flashcard, Sarah, event, and notification endpoints.
- Local files: Room records and app-private storage for PDFs and other picked files; non-PDF files open through compatible phone apps.
- Offline study: device-local personal Cards, their materials, and Notes are persisted in Room/app-private storage under AD-083.
- PDF: existing Room library, local renderer, text extractor, adjustable lasso selection, and page-based Sarah context for Card Resources.
- Material processing: supported Card uploads are extracted server-side; backend OCR handles images, image-only PDF pages, and embedded document images. The Android reader itself does not perform OCR.
- Networking: Retrofit, Kotlin serialization, OkHttp, and Hilt. The client contains no provider keys or authoritative authorization rules.

## 6.1 Firebase setup

Add the Firebase Console-generated google-services.json to frontend/app/ and enable Email/Password sign-in for Android package com.aipdfreader.app. Set the backend LUMIRA_FIREBASE_PROJECT_ID to that same Firebase project. The Android file contains project-specific public client identifiers; backend Admin credentials must remain on the server.

## 7. Known limitations / next steps

- **Firebase UID provisioning**: AD-082 defers the trusted process that
  maps authenticated Firebase users to Vision users. Protected app APIs
  remain unavailable until that mapping is made.
- **Profile/feedback backend**: learner profile details and photos are
  device-local for now. Feedback uses the Android share sheet. A trusted
  profile/provisioning API and feedback submission endpoint are later
  backend/API work. Local Card sync depends on deployment of the API endpoint
  and Firebase UID provisioning.
- **Android Studio validation**: the client has not yet been built or
  exercised on an emulator. Add google-services.json, sync/build, and debug
  before treating the Android implementation as complete.
- **Account deletion**: AD-082 requires successful Vision account deletion
  to also remove or disable its Firebase identity. That cross-system backend
  behavior must be confirmed before wiring account deletion in the client.
- **Scanned/image-only pages**: local extraction (PDFBox) has no OCR. For a PDF Resource uploaded to a Card, a lasso selection with no local text can open Sarah with the page index; Sarah uses any server-side OCR text extracted from the Resource. The current Sarah API is text-only and does not receive the lasso crop, so pages or images with no OCR-readable text cannot yet be visually interpreted. Local-only files must first be added to a Card to use Sarah.
- **Selection Engine status**: see `docs/engineering/` for exactly what's
  implemented (foundation layer + text layout engine as of this writing)
  versus what's planned next (polygon–text intersection, OCR fallback).
