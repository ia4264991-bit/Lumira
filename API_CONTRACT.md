# Lumira API Contract & Cross-Cutting Notes

> ⚠️ **RETIRED — 2026-09-12.** This file described the network boundary for
> the old academic-hierarchy architecture. **It is no longer authoritative.**
> A new API contract will be derived from the new prototype-first
> architecture once that architecture exists — this file is not it yet, and
> nothing below should be treated as the target to build toward.
>
> **What is still factually true, kept for reference only:** the DTO shapes
> below (`AuthApi.kt`, `AiRouterApi.kt`, `AuthDto.kt`, `AiDto.kt`) describe
> code that genuinely still exists in the frontend today — that part isn't
> retired-as-in-wrong, it's retired-as-in-not-yet-reconsidered. Auth and an
> "ask Sarah" endpoint will very likely still be needed in the new
> architecture too; this file just no longer gets to assume its exact shape
> survives unchanged. Treat everything below as historical input to the new
> contract, not as its current state.

---

## What existed as of retirement (2026-09-03 → 2026-09-12)

| Endpoint | Status | Notes |
|---|---|---|
| `GET /v1/health` | Was LIVE | `common/HealthController.java` — this one has no domain-model dependency and remains real/working regardless of architecture. |
| `POST /v1/auth/{register,login,refresh}` | Was PROPOSED, never implemented | Frontend (`AuthApi.kt`) already has working Kotlin/Retrofit interfaces + DTOs for these — real code, just never met by a backend. |
| `POST /v1/ai/ask` | Was PLANNED, never proposed in detail | Frontend (`AiRouterApi.kt`) already sends `documentId`/`selectedText`/`question`/`conversationHistory` — this shape predates Sarah's elevation to a first-class workspace capability and will very likely need to change (e.g. to carry a Card/Course Space id for non-selection-based "workspace Sarah" conversations).

## Historical DTO shapes (frontend code, still on disk, not yet reconsidered)

```json
// AuthDto — register/login/refresh response
{ "accessToken": "string", "refreshToken": "string", "email": "string" }
```

```json
// AiDto — AskRequest (existing "contextual Sarah" shape)
{
  "documentId": "string | null",
  "pageIndex": "int | null",
  "selectedText": "string | null",
  "question": "string",
  "conversationHistory": [{ "role": "user | assistant", "content": "string" }]
}
// AskResponse
{ "answer": "string | null", "conversationId": "string | null", "error": "string | null" }
```

**Known gap, worth carrying into the new contract design:** this shape has
no field representing "which Card/Course Space grounds this conversation"
for workspace-level Sarah entry (tap Sarah from inside a Card, no text
selected) — only resource-selection-based context. The new architecture
should account for this rather than silently inheriting the gap.

## Known temporary shims (still real, still present in the frontend)

- **`DebugAuthBypass.kt`**: seeds a fake local session so the Reader/
  Selection Engine can be exercised without a real backend. Still in place;
  removing it is now gated on the *new* auth architecture existing, not the
  old one.
- Frontend's `applicationId` is still `com.aipdfreader.app` — unaffected by
  this retirement, still a deferred, separate rename.

## What is explicitly NOT carried forward as authoritative

The prior "Module ownership (backend)" section describing `user`/`course`/
`resource`/`security`/`ai` in terms of LPTS chapters and `CourseOffering`/
`CourseOfferingMembership` has been removed from this file. That module
scope belonged to the retired architecture. See `backend/README.md` for the
current, neutral status of those (empty) packages.

---

## Changelog

- **2026-09-03** — Original contract created after an audit found the
  frontend wired against endpoints the backend didn't have.
- **2026-09-12** — Retired. Old academic-hierarchy framing and module
  ownership removed. Historical DTO shapes preserved for reference only.
  New contract to be derived from the new prototype-first architecture.
