# Lumira API Contract & Cross-Cutting Notes

**This file is the source of truth for anything that crosses the
backend/frontend boundary.** Backend and frontend are developed in
separate sessions (see root `README.md` → "Working process"); this file
exists so neither side has to guess at the other's shape.

## Update protocol

- If a change you're making adds, removes, or alters a REST endpoint,
  request/response shape, header, or error format — **update this file in
  the same commit.**
- Add a line to the changelog at the bottom with the date, which side made
  the change, and a one-line summary.
- If you're starting work and this file says something your side's code
  doesn't match yet, **this file wins** — either implement to match it, or
  update it and flag the mismatch in your commit message.
- Endpoints marked `PROPOSED` are not live yet. Anyone implementing a
  `PROPOSED` endpoint should flip it to `LIVE` in the same commit that
  ships it, and correct the shape here if reality ends up differing from
  the proposal.

---

## Status summary (as of 2026-09-03)

| Endpoint | Status | Backend impl | Frontend caller |
|---|---|---|---|
| `GET /v1/health` | **LIVE** | `common/HealthController.java` | not called by app UI |
| `POST /v1/auth/register` | **PROPOSED** | not implemented | `AuthApi.kt` (already coded, will 404) |
| `POST /v1/auth/login` | **PROPOSED** | not implemented | `AuthApi.kt` (already coded, will 404) |
| `POST /v1/auth/refresh` | **PROPOSED** | not implemented | `AuthApi.kt` (coded but not wired into an `Authenticator` yet — see frontend README §7) |
| `POST /v1/ai/ask` | **PLANNED, NOT PROPOSED YET** | not implemented (LPTS Ch. 7–9, backend `ai/` module is empty by design) | `AiRouterApi.kt` (already coded, will 404) |

The frontend was built ahead of the backend and already contains working
Kotlin/Retrofit interfaces + `kotlinx.serialization` DTOs for the rows
above marked "already coded." Those shapes are the *proposal* below —
changing them means changing shipped client code, so the backend should
implement to match unless there's a concrete reason not to (note it here
if so).

---

## LIVE endpoints

### `GET /v1/health`
No auth required.

Response `200`:
```json
{ "status": "UP", "service": "lumira-backend", "timestamp": "2026-09-03T12:00:00Z" }
```

---

## PROPOSED endpoints (auth — next implementation step)

Base path: `/v1/auth/`. None of these require a bearer token (see
"Auth header" below — the frontend's `AuthInterceptor` already knows to
skip any path containing `/auth/`, via `Constants.AUTH_PATH_SEGMENT`).

### `POST /v1/auth/register`
Request:
```json
{ "email": "string", "password": "string" }
```
Response `200` (proposed — see AuthResponse below):
```json
{ "accessToken": "string", "refreshToken": "string", "email": "string" }
```
Error case: see "Error shape" below.

### `POST /v1/auth/login`
Request:
```json
{ "email": "string", "password": "string" }
```
Response: same `AuthResponse` shape as register.

### `POST /v1/auth/refresh`
Request:
```json
{ "refreshToken": "string" }
```
Response: same `AuthResponse` shape.

**Note:** frontend doesn't yet call this automatically on a 401 — a 401
today surfaces to the user as "please sign in again." Wiring an OkHttp
`Authenticator` is a known frontend follow-up, independent of backend
work.

---

## PLANNED (not yet proposed in detail) — `/v1/ai/ask`

Frontend already has a concrete shape in `AiDto.kt` (`AskRequest` /
`AskResponse`), reproduced here for backend visibility, but this is
**not** being proposed as the implementation target yet — Chapters 7–9
(Sarah / AI routing) haven't been scoped on the backend side. Treat this
section as "here's what the client currently sends," not "please build
this."

```json
// AskRequest
{
  "documentId": "string | null — currently the client's LOCAL Room id, not a backend id (no doc-sync endpoint exists yet)",
  "pageIndex": "int | null",
  "selectedText": "string | null",
  "question": "string",
  "conversationHistory": [{ "role": "user | assistant", "content": "string" }]
}
```
```json
// AskResponse
{ "answer": "string | null", "conversationId": "string | null", "error": "string | null" }
```

Contract principle (from LPTS §32, "Android is a thin client"): this
request must never carry an AI provider name, model name, system prompt,
or API key. The backend owns provider selection and prompt construction
entirely.

---

## Shared conventions

- **Versioning:** all endpoints are prefixed `/v1/`.
- **Auth header:** `Authorization: Bearer <accessToken>`, attached
  automatically by the frontend's `AuthInterceptor` to every request
  *except* any path containing `/auth/`.
- **Error shape:** frontend expects errors deserializable as:
  ```json
  { "message": "string | null" }
  ```
  (see `ApiErrorBody` in `AuthDto.kt`). Backend should return this shape
  in the body for 4xx/5xx auth responses if practical, since the frontend
  doesn't currently have bespoke per-status-code handling beyond that.
- **Base URL:** frontend has no runtime backend-URL setting — it's a
  build-time `BuildConfig.BACKEND_BASE_URL`, defaulting to
  `http://10.0.2.2:8080/` (Android emulator's alias for host `localhost`)
  in debug builds.

---

## Known temporary shims (don't be confused by these)

- **`DebugAuthBypass.kt`** (frontend, debug builds only): seeds a fake
  session (`debug-bypass-access-token` / `debug-bypass-refresh-token` /
  `debug-tester@aipdfreader.local`) into local storage on first launch so
  the Reader/Selection Engine can be exercised on-device without a real
  backend. It never calls the network. **Once `/v1/auth/login` is real and
  wired end-to-end, delete this file and its one call site** in
  `AiPdfReaderApp.onCreate()** — nothing else references it.
- Frontend's Gradle `namespace`/`applicationId` are still
  `com.aipdfreader.app` (pre-rebrand). Display name/icon/persona are
  "Lumira"/"Sarah"; the package rename is a deliberately deferred, separate
  change (touches `FileProvider` authority, `BuildConfig`, all imports).

## Module ownership (backend)

Per each module's `package-info.java` — check there for the authoritative,
current statement; this is a summary, not a substitute:

- `user` — minimal `User` entity only in this slice (just enough for FK
  targets); full auth/roles/permissions is `security` (Ch. 15), not here.
- `course` — `Course`/`CourseOffering`/`CourseOfferingMembership` (Ch. 3).
  Membership `role` is a label only; permissions are `security`'s concern.
- `resource` — `Resource`/`ResourceVersion` (Ch. 4), upload lifecycle,
  versioning, viewer extraction. Does **not** own chunking/embeddings —
  that's a future Processing Pipeline module.
- `security` — empty by design in this slice; reserved for Ch. 15.
- `ai` — empty by design in this slice; reserved for Ch. 7–9.

## Module ownership (frontend)

Per `frontend/README.md` §6 — Android is a thin client: no AI provider
keys/logic, no prompt construction, single backend base URL, all AI access
goes through `/v1/ai/ask`. Selection Engine milestones (lasso capture,
text layout, polygon–text intersection) are tracked separately in
`frontend/docs/engineering/`, not here — this file only covers the
network/API boundary.

---

## Changelog

- **2026-09-03** — Claude (audit session): created this file after an
  audit found the frontend fully wired against `/v1/auth/*` and
  `/v1/ai/ask`, neither of which exist on the backend yet. No code
  changed; this is the first shared contract doc for the repo.
