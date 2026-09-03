# Lumira (Android) — Thin Client

An Android app for opening PDFs, selecting text from them, and asking
Sarah — the in-app AI assistant — questions about the selected passage.
Built with Kotlin, Jetpack Compose, MVVM, and Room.

*Note: the underlying Gradle `namespace`/`applicationId` remain
`com.aipdfreader.app` for now — this was a display-name/branding rename
only (app name, launcher icon, in-app assistant persona), not a package
rename. Renaming the applicationId is a larger, separate change (it touches
the FileProvider authority, generated `BuildConfig`, and every import in the
codebase) and hasn't been done here.*

**This client holds no AI provider logic.** It authenticates against our own
backend and sends it plain, descriptive requests ("the user selected this
passage and asked this question"). The backend owns provider selection,
prompt engineering, API keys, OCR, and context building. See
`ENGINEERING_REPORT.md` for the full rationale and change log of this
architecture.

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

## 4. Authentication

On first launch, the app shows a **Sign in / Register** screen. Credentials
are sent to `POST /v1/auth/login` or `/v1/auth/register` (see
`AuthApi.kt`); the backend's session token is stored locally and attached
as a `Bearer` header to every subsequent request by `AuthInterceptor`. The
app never stores or transmits any AI provider key — it has no concept of
one.

## 5. Using the app

Feature set is unchanged from the original standalone build:

1. **Library** → **Add PDF** → pick a file via the system document picker.
2. Tap a document to open the **Reader**; pinch to zoom, drag to pan.
3. Tap the **text icon** to open the selectable text panel for the current
   page (long-press to select, copy, then **Use copied text**).
4. **Highlight** to save a passage, or **Ask AI** to open a chat scoped to
   it.
5. In **Chat**, ask follow-ups — the app forwards your question and the
   selected passage to the backend's AI Router; it does not construct a
   prompt itself.
6. Tap the **account icon** on the Library screen to see your session or
   sign out.

## 6. Architecture

```
app/
 ├─ data/
 │   ├─ local/
 │   │   ├─ (Room) entities, DAOs, AppDatabase
 │   │   └─ session/         SessionTokenStore — OUR backend's auth tokens only
 │   ├─ remote/
 │   │   ├─ AuthApi.kt        POST /v1/auth/{login,register,refresh}
 │   │   ├─ AiRouterApi.kt    POST /v1/ai/ask  (the ONLY AI-related call)
 │   │   ├─ AuthInterceptor.kt Attaches our bearer token to every request
 │   │   └─ dto/              AuthDto.kt, AiDto.kt — our own wire contracts
 │   └─ repository/          PdfRepository, HighlightRepository,
 │                            ChatRepository, AiRepository, AuthRepository
 ├─ domain/model/            Plain Kotlin models used by the UI layer
 ├─ pdf/                     Local PDF rendering (zoom) + text extraction
 │                           (feeds the selection UI, NOT the AI request)
 ├─ di/                      Hilt modules: AppModule (Room), NetworkModule
 │                           (Retrofit/OkHttp/API interfaces)
 ├─ ui/
 │   ├─ auth/                Login/Register screen
 │   ├─ account/             Session info + sign out (replaces old AI Settings)
 │   ├─ library/             PDF picker + library list
 │   ├─ reader/              PDF viewer, zoom, text selection, highlights
 │   ├─ chat/                AI chat screen
 │   ├─ navigation/          Compose Navigation graph (Login-gated)
 │   └─ theme/               Material3 theme
 └─ util/                    FileUtils (SAF import), Constants
```

The client-server/thin-client architecture change is described in this
README's history; ongoing Selection Engine work (lasso selection, text
layout, future intersection/OCR) has its own maintained engineering
documentation — see `docs/engineering/README.md` for the index of
per-milestone reports (`docs/engineering/M1.0_Foundation_Layer.md`,
`docs/engineering/M1.1_Text_Layout_Engine.md`, and so on as new milestones
complete).

## 7. Known limitations / next steps

- **Token refresh**: `AuthApi.refresh()` exists but isn't wired into an
  OkHttp `Authenticator` yet — a 401 currently surfaces as "please sign in
  again" rather than transparently refreshing.
- **Document sync**: `AskRequest.documentId` currently sends the client's
  local Room id as a best-effort reference. Once a document-upload/sync
  endpoint exists, this should carry the backend's own document id instead.
- **Scanned/image-only PDFs**: local extraction (PDFBox) has no OCR: the
  selection panel will show "No extractable text found" for scanned pages.
  Server-side OCR (already in the target architecture) is the right place
  to solve this for AI answers; wiring a page-image upload path for that is
  a recommended next milestone.
- **Selection Engine status**: see `docs/engineering/` for exactly what's
  implemented (foundation layer + text layout engine as of this writing)
  versus what's planned next (polygon–text intersection, OCR fallback).
