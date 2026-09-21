# Lumira API Contract

**Status: LIVE, rebuilt 2026-09-12** from `docs/DECISIONS.md` (AD-019–065),
`docs/DOMAIN_MODEL.md`, `docs/RESOURCE_FILE_PROCESSING_SPEC.md`,
`docs/SARAH_SECURITY_SPEC.md`, and `docs/IMPLEMENTATION_PLAN.md`. This
supersedes the prior retired/historical version of this file (its content
is preserved below under "Historical" for reference).

**This document describes only behavior already architecturally decided.**
Anything not traceable to one of the five documents above is a bug in this
contract, not a new decision. Every endpoint below is tagged:

- 🔒 **Frozen** — required by an existing AD/specification.
- ⏸ **Deferred** — explicitly not part of this contract; named here only
  so an implementation agent doesn't invent it.
- ⚙️ **Implementation detail** — the shape shown is illustrative; the exact
  field/type may be chosen at implementation time without changing the
  domain contract.

---

## Conventions

- Base path: `/v1/`.
- IDs: UUID, as strings in JSON.
- Auth header: `Authorization: Bearer <token>` — see **Identity** below for
  what's actually decided about the token itself (not much, deliberately).
- Error shape ⚙️: `{ "error": { "code": "string", "message": "string" } }`
  — exact code taxonomy is implementation detail.
- Pagination ⚙️: where a list endpoint could return an unbounded
  collection, a `page`/`pageSize` or cursor parameter is expected: exact
  mechanism (offset vs. cursor/keyset) is explicitly deferred
  (`docs/DECISIONS.md` closure-pass notes) — implementations may choose
  either without this being a contract change.

---

## Identity

⏸ **Deferred, stated plainly rather than invented:** no AD defines a full
authentication/session/token architecture. `docs/DOMAIN_MODEL.md` §3
explicitly marks this deferred; `docs/IMPLEMENTATION_PLAN.md`'s B1 scopes
only "the minimal boundary needed to attribute a Card to a user."

🔒 **What is decided:** every protected endpoint below requires the
backend to resolve a `userId` from the request and evaluate authorization
against **live** domain state for that user (AD-056) — never from a
client-supplied claim, a cached permission, or the mere existence of an
object ID (AD-056, AD-058).

```
GET /v1/me
```
- Auth: required.
- Response: `{ "userId": "uuid", "email": "string" }` ⚙️ (exact profile
  fields beyond `userId` are implementation detail — no AD specifies a
  User profile shape beyond what's needed for ownership attribution).

---

## Cards

🔒 AD-019, AD-048: a User may own zero or more Cards; no primary Card; no
separate `CourseSpace` resource anywhere in this API.

```
POST /v1/cards
```
- Auth: required.
- Request: `{ "name": "string", "color": "string" }` ⚙️ (fields match the
  validated UX prototype; not independently frozen by an AD, but not in
  tension with one either).
- Response `201`: `{ "id": "uuid", "ownerId": "uuid", "name": "string", "color": "string", "isShared": false, "createdAt": "ISO-8601" }`.
- Authorization: the caller becomes `ownerId`. No further check needed —
  creating a Card requires only being authenticated.

```
GET /v1/cards
GET /v1/cards?scope=shared
```
- Auth: required.
- 🔒 Two **filtered views of the same underlying resource** (AD-019) —
  `scope=shared` returns Cards where `isShared=true` that the caller owns
  or is an active member of; the default (no `scope`) returns every Card
  the caller owns. There is no separate "Course Spaces" endpoint family.
- Response: paginated array of the Card shape above, each also including
  `role` (`OWNER`/`ADMIN`/`MEMBER`, AD-023) when returned in the
  `scope=shared` view, from the caller's own `card_membership` row.

```
GET /v1/cards/{cardId}
```
- Auth: required.
- Authorization 🔒: caller must be the owner, or hold an `ACTIVE`
  `card_membership` row for this Card (AD-056, live check — not cached).
- Response `200`: Card shape, `404` if not found or not authorized (never
  distinguish "doesn't exist" from "not authorized" in the response body —
  AD-056's BOLA/IDOR guard).

---

## Course Space (Card capability — AD-019, no separate entity)

Every endpoint below operates on `/v1/cards/{cardId}/...` — there is no
`/v1/course-spaces/` path anywhere in this contract.

### Enabling sharing

```
POST /v1/cards/{cardId}/share
```
- Auth: required. Authorization 🔒: caller must be the Card's owner.
- Effect: sets `isShared=true`; creates an `OWNER` `card_membership` row
  for the caller if one doesn't already exist. No request body needed.
- Response `200`: updated Card shape.

### Invite link (AD-024, AD-062)

```
POST /v1/cards/{cardId}/share-link          — generate/reset
GET  /v1/cards/{cardId}/share-link          — view current link
```
- Authorization 🔒: Owner or Admin may view/share; **only the Owner**
  may generate/reset (AD-024).
- 🔒 AD-062: resetting **invalidates any pending join request** submitted
  via the link being replaced — those requests are marked void, not left
  pending against a dead link.
- Response: `{ "shareToken": "string", "url": "string", "requireApproval": "boolean" }`.

```
PATCH /v1/cards/{cardId}/share-link/approval
```
- Request: `{ "requireApproval": "boolean" }`.
- Authorization: Owner (AD-025's approval toggle is an Owner-level
  setting per the existing decision).

### Joining

```
POST /v1/join/{shareToken}
```
- Auth: required.
- 🔒 AD-025: if `requireApproval=false`, this immediately creates the
  joiner's new Card (auto-created, `role=MEMBER`, Resource references to
  the origin's currently-shared Resources per AD-021/045 — never copies)
  and an `ACTIVE` `card_membership` row. If `requireApproval=true`, this
  creates a `PENDING` join request instead (a request, not yet a
  membership row) — see approval endpoints below.
- 🔒 AD-033: if the caller already owns a Card that was previously linked
  to this same Course Space (a rejoin), a **new** `card_membership`
  episode is created, linked to that **existing** Card — never a new Card,
  never a new identity.
- Response `201` (immediate join): the joiner's Card shape.
- Response `202` (pending approval): `{ "status": "PENDING" }`.
- Error: `410 Gone` if the token was reset/invalidated (AD-062).

```
GET  /v1/cards/{cardId}/join-requests        — Owner/Admin only, pending requests
POST /v1/cards/{cardId}/join-requests/{requestId}/approve
POST /v1/cards/{cardId}/join-requests/{requestId}/reject
```
- Authorization: Owner or Admin.
- Approving performs the same auto-Card-creation/reuse behavior described
  above for immediate joins.

### Membership (AD-023, AD-032, AD-060)

```
GET /v1/cards/{cardId}/members
```
- Authorization: any active member.
- Response: array of `{ "userId": "uuid", "status": "ACTIVE|LEFT|REMOVED|INVITED", "role": "OWNER|ADMIN|MEMBER", "joinedAt": "ISO-8601" }`.
- 🔒 AD-060: at most one `ACTIVE`/`INVITED` row per `(cardId, userId)` —
  the backend enforces this at the database layer; the API surface never
  exposes duplicate current memberships for the same user.

```
POST /v1/cards/{cardId}/members/{userId}/promote     — AD-034, Owner only
POST /v1/cards/{cardId}/members/{userId}/demote       — AD-034, Owner only
DELETE /v1/cards/{cardId}/members/{userId}            — remove; AD-034, Owner or Admin
POST /v1/cards/{cardId}/leave                         — self-initiated (AD-042 blocks the Owner)
```
- 🔒 AD-034: promote/demote is Owner-only; Admins cannot grant or revoke
  Admin status for anyone.
- 🔒 AD-042: `POST .../leave` returns `409 Conflict` with a reason
  indicating "transfer or dissolve required" if the caller is the current
  Owner — the Owner cannot leave via this endpoint under any
  circumstance without first transferring ownership.
- 🔒 AD-063: dissolution (below) is the alternative to transfer, not to
  this endpoint.

### Ownership transfer (AD-042, AD-043, AD-061)

```
POST /v1/cards/{cardId}/transfer-ownership
```
- Request: `{ "targetUserId": "uuid" }`.
- Authorization: current Owner only.
- 🔒 AD-061: target eligibility (must hold an `ACTIVE` membership) is
  revalidated **at completion**, inside the same transaction — if the
  target's membership changed since the request was received, this fails
  with `409 Conflict` rather than completing against a stale target.
- 🔒 AD-043: single atomic operation — no multi-step workflow, no
  invitation/acceptance step.
- Effect: emits `OWNERSHIP_TRANSFERRED` event (AD-026 extended types).

### Dissolution (AD-051, AD-063)

```
DELETE /v1/cards/{cardId}/share
```
- Authorization: current Owner only.
- 🔒 AD-063: deactivates all active share records for this Course Space;
  never deletes any artifact, never changes any artifact's ownership,
  never touches any member's private content. Sets `isShared=false`.
  Historical events remain (AD-052).

---

## Resources (AD-021, AD-057, `RESOURCE_FILE_PROCESSING_SPEC.md`)

```
POST /v1/cards/{cardId}/resources
```
- Auth: required. Authorization: caller owns `{cardId}` or holds `ACTIVE`
  membership with upload rights (Owner/Admin per AD-023 for a Course
  Space; any owner for their own private Card).
- Request: multipart upload — file bytes + `{ "title": "string" }`.
- 🔒 Processing executes **synchronously, in-process, for MVP** — no
  queue/worker. The response is only returned once processing reaches
  `READY` or `FAILED` (`RESOURCE_FILE_PROCESSING_SPEC.md` §4).
- Response `201`: `{ "id": "uuid", "ownerCardId": "uuid" (nullable), "ownerUserId": "uuid" (nullable), "title": "string", "mimeType": "string", "status": "READY|FAILED", "failureReason": "string|null", "createdAt": "ISO-8601" }`.
- 🔒 AD-057: exactly one of `ownerCardId`/`ownerUserId` is non-null — the
  API surface reflects this directly rather than hiding it behind a
  single `ownerId`.
- No `resourceVersion` field, no version history endpoint — versioning is
  explicitly deferred; a Resource has one current extracted representation
  only, per `IMPLEMENTATION_PLAN.md`'s B3.

```
GET /v1/cards/{cardId}/resources
GET /v1/resources/{resourceId}
```
- Authorization: same as upload — private-Card-owner, or authorized
  Course-Space member for a shared Resource (AD-045's share record
  governs the latter, checked live per AD-056).

```
POST /v1/resources/{resourceId}/reprocess
```
- Authorization: owner of the Resource's owning Card/User.
- 🔒 Per `RESOURCE_FILE_PROCESSING_SPEC.md` §4: reprocessing runs against
  the **same** Resource row (idempotent), never creates a duplicate.

---

## Sharing (AD-045, AD-044, AD-046, AD-047, AD-050 §OQ-8 authority matrix)

```
POST   /v1/artifacts/{artifactType}/{artifactId}/share       — into {cardId}
DELETE /v1/artifacts/{artifactType}/{artifactId}/share/{cardId}   — owner unshare
POST   /v1/artifacts/{artifactType}/{artifactId}/force-unshare/{cardId}
```
- `artifactType` ∈ `resource | note | studyset | summary | quiz | flashcardset`.
- 🔒 AD-045: this is an **explicit share record**, not a same-row boolean
  — the API models it as its own relationship (share/unshare as distinct
  operations on a link), never as a field toggle on the artifact itself.
- 🔒 AD-044 authorization matrix, enforced exactly:

| Caller | Endpoint | Allowed? |
|---|---|---|
| Artifact owner | unshare (their own) | Always |
| Course Space Owner | force-unshare (anyone's) | Always |
| Course Space Admin | force-unshare (anyone's) | Only with `reason` supplied |
| Ordinary Member | any of the above on others' content | Never — `403` |

- Force-unshare request: `{ "reason": "COPYRIGHT|PRIVACY|SAFETY|ABUSE|MALICIOUS_CONTENT|POLICY_VIOLATION|OTHER", "note": "string (optional)" }` (AD-047).
- 🔒 Force-unsharing never deletes the artifact or changes ownership —
  response `200` returns the artifact unchanged except its share state.
- Effect: emits `CONTENT_UNSHARED` or `CONTENT_FORCE_UNSHARED` (AD-046);
  the artifact's owner receives a notification on force-unshare, derived
  from that event, not a separate notification call.

---

## Notes / Study Sets / Summaries (AD-029, AD-030)

```
POST /v1/cards/{cardId}/notes
GET  /v1/cards/{cardId}/notes
GET  /v1/notes/{noteId}
```
(Identical shape/pattern for `/studysets/` and `/summaries/`.)
- 🔒 AD-030: one entity type each — no `PersonalNote`/`CourseSpaceNote`
  split anywhere in this contract. Sharing is via the generic
  `/share`/`/force-unshare` endpoints above, applied with
  `artifactType=note|studyset|summary`.

---

## Quizzes and Attempts (AD-029, AD-030, AD-053)

```
POST /v1/cards/{cardId}/quizzes
GET  /v1/quizzes/{quizId}
```
- Canonical Quiz structure/content only — no attempt data here.

```
POST /v1/quizzes/{quizId}/attempts
GET  /v1/quizzes/{quizId}/attempts/me
```
- 🔒 AD-053: `QuizAttempt` is a **separate, per-user** record — never
  merged into or mutating the `Quiz` row. `GET .../attempts/me` returns
  only the caller's own attempts; there is no endpoint to view another
  user's attempts against a shared Quiz (AD-028's isolation principle
  applied to study state).
- ⚙️ Exact attempt schema (how it binds to specific quiz content,
  scoring representation) is deferred — the boundary is frozen, the shape
  isn't.

---

## Flashcards and Progress (AD-029, AD-030, AD-053)

```
POST /v1/cards/{cardId}/flashcard-sets
GET  /v1/flashcard-sets/{setId}
POST /v1/flashcard-sets/{setId}/progress
GET  /v1/flashcard-sets/{setId}/progress/me
```
- Same canonical-vs-personal-state pattern as Quizzes, above.

---

## Events and Notifications (AD-026, AD-039)

```
GET /v1/cards/{cardId}/events
```
- 🔒 AD-026: this **is** the Updates feed — a direct read of the
  append-only event log, not a separately-maintained "activity" table.
- Response: array of `{ "id": "uuid", "type": "string", "actorUserId": "uuid", "createdAt": "ISO-8601", "payload": "object" }`. `type` is open/
  string-valued (AD-026) — not a closed enum in the contract.

```
GET   /v1/notifications
PATCH /v1/notifications/{id}/read
```
- 🔒 AD-026: a notification is a delivery/read-state wrapper around an
  event (`eventId` field present on every notification) — never a
  freestanding fact. There is no endpoint to create a notification
  directly; they only ever arise as a side effect of an event.
- ⏸ Push delivery (FCM or similar) is explicitly deferred — this contract
  covers only in-app read/unread state.

---

## Sarah

🔒 **AD-027**: two entry points.

```
POST /v1/cards/{cardId}/sarah/ask
```
- Request: `{ "question": "string", "conversationHistory": [...] }` —
  **Workspace Sarah** (grounded in the whole Card).

```
POST /v1/resources/{resourceId}/sarah/ask
```
- Request: `{ "selectedText": "string", "question": "string", "conversationHistory": [...], "pageIndex": "int|null" }`
  — **Contextual Sarah**, grounded in that Resource plus the workspace.
  *(This is `API_CONTRACT.md`'s previously-flagged gap, now closed: the
  historical `AskRequest` shape had no `cardId`; both endpoints above
  carry it implicitly via the path, which resolves the gap without
  needing an explicit body field.)*

**🔒 Authorization requirements, stated as contract-level obligations, not
just an architecture pointer** (`SARAH_SECURITY_SPEC.md`):
- Authorization is resolved **before** any content enters retrieval
  (AD-054) — never retrieve-then-filter.
- Authorization is revalidated **at the moment of each protected access**
  within the request's lifecycle, not only at request start (AD-058) —
  this applies even if the backend implementation involves multiple
  internal retrieval steps for one API call.
- Retrieved or generated content — including content shaped like an
  instruction — is data; it never independently authorizes a further
  retrieval or tool call (AD-058).
- Provenance (below) never grants access to a cited source (AD-059).
- For `Course Space Sarah` (the first endpoint, when `{cardId}` is a
  shared Card): retrieval is limited to that Course Space's currently
  -shared content plus the caller's own private content — never another
  member's private content, regardless of membership (AD-028).

- Response: `{ "answer": "string", "conversationId": "uuid" }`.
- 🔒 AD-037: the backend selects and calls the model provider; **no
  provider name, model ID, or credential appears anywhere in this
  request/response shape** — that boundary is entirely server-side (the
  "AI Router"), not exposed to Android at all.
- 🔒 AD-037: usage/limit state is **server-authoritative**. This endpoint
  (or a paired one) returns a usage indicator:
  `{ "usageUsed": "int", "usageLimit": "int" }` ⚙️ (exact metering unit
  and limit numbers are operational parameters, not frozen) — the client
  displays this value, it never computes its own.

### Sarah generation (AD-035, AD-036, AD-055, AD-065)

```
POST /v1/cards/{cardId}/sarah/generate
```
- Request: `{ "artifactType": "summary|flashcardset|quiz|studyset", "sourceResourceIds": ["uuid", ...], "instructions": "string|null" }`.
- 🔒 AD-035: all four types are in MVP scope.
- 🔒 AD-055 + AD-065: output is validated as a **complete whole before
  persistence** — if any required component fails validation, the entire
  attempt is rejected; there is no partial-success response shape.
- Response `201` (success): the generated artifact, in the **exact same
  shape** as its manually-created counterpart (AD-036) — no separate
  "generated artifact" response type — plus a `provenance` field:
  `{ "generatedBy": "SARAH", "sources": [{ "artifactType": "string", "artifactId": "uuid" }] }`.
  🔒 AD-059: this `provenance.sources` list is informational only —
  possessing a generated artifact never grants access to the sources it
  lists; those remain governed entirely by their own ownership/sharing
  state.
- Response `422` (validation failure): `{ "error": { "code": "GENERATION_VALIDATION_FAILED", "message": "string" } }` — the client may retry; nothing partial was persisted.

---

## Account Deletion (AD-049–052)

```
DELETE /v1/me
```
- Auth: required (re-authentication may be required — ⚙️ implementation
  detail).
- 🔒 AD-051: returns `409 Conflict` with a list of Course Spaces where
  the caller is sole Owner, if any exist — deletion cannot proceed until
  each is transferred (`POST .../transfer-ownership`) or dissolved
  (`DELETE .../share`) first. No automatic transfer or dissolution happens
  as a side effect of this call.
- 🔒 On success: all private Cards and their private artifacts, Sarah
  private history/derived data, and active sessions are permanently
  deleted (AD-049). Artifacts currently shared into an active Course
  Space are **not** deleted — their owning-Card reference is reassigned
  to that Course Space's own Card (AD-050), including a shared Sarah
  conversation if one exists (AD-022/050). Historical events referencing
  the deleted user as actor are retained (AD-052).
- Response `204` on success.

---

## Explicitly out of scope for this contract

Named here so no implementation agent invents them: payment/subscription
endpoints, billing, search/vector/embedding endpoints, any Kafka/RabbitMQ
or generic queue-status endpoint, distributed cache introspection, a
`ContentVersion`/version-history endpoint, a `SarahSession` resource, an
explicit event-sequence-number field, or a direct-object-storage
presigned-upload endpoint. All remain deferred per `docs/DECISIONS.md`'s
closure-pass notes — none are contradicted here, simply not yet in scope.

---

## Historical (superseded, kept for reference only)

The prior version of this file described `/v1/auth/{register,login,refresh}`
and a `/v1/ai/ask` shape without a `cardId`, against the retired
academic-hierarchy architecture. Those DTOs are preserved in this
repository's git history (see commit `f46883d` and earlier) — not
repeated here, since the shapes above supersede them entirely.

## Changelog

- **2026-09-03** — Original contract created.
- **2026-09-12** — Retired (academic-hierarchy architecture retirement).
- **2026-09-12** — Rebuilt as authoritative, derived from AD-019–065 and
  the four governing specification/domain documents.
