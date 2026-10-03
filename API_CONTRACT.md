# Lumira API Contract

**Status: LIVE, rebuilt 2026-09-12 and extended through 2026-10-03** from `docs/DECISIONS.md` (AD-019–072),
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

## Course Space (Card capability - AD-019, no separate entity)

Every endpoint below operates on `/v1/cards/{cardId}/...`; there is no `/v1/course-spaces/` resource.

### Enabling sharing

```http
POST /v1/cards/{cardId}/share
```

- Auth required; only the Card owner may enable sharing.
- Sets `isShared=true` and creates the Owner's ACTIVE/OWNER membership if absent. That membership's `memberCardId` is the Course Space Card itself (AD-067).
- Response `200`: updated Card.

### Invite link (AD-024, AD-062, AD-069)

```http
POST  /v1/cards/{cardId}/share-link
GET   /v1/cards/{cardId}/share-link
PATCH /v1/cards/{cardId}/share-link/approval
```

- Owner and ACTIVE Admin may view/share the current link; only Owner may create/reset it.
- Reset rotates the invite token and its separate `inviteTokenVersion`. It immediately invalidates the previous link and every PENDING join request tied to the prior version. No time-based expiry.
- Response: `{ "shareToken": "string", "url": "https://...", "requireApproval": "boolean" }`. The version is persisted for request binding; it is not the invite secret and is not returned to clients.
- Approval-toggle request: `{ "requireApproval": "boolean" }`; Owner only.

### Joining and approval (AD-025, AD-033, AD-062, AD-067, AD-069)

```http
POST /v1/join/{shareToken}
GET  /v1/cards/{cardId}/join-requests
POST /v1/cards/{cardId}/join-requests/{requestId}/approve
POST /v1/cards/{cardId}/join-requests/{requestId}/reject
```

- Join requires authentication. A stale or reset token returns `410 Gone`.
- If approval is disabled, the request immediately creates an ACTIVE MEMBER membership and creates or reuses that user's member Card. A first join creates one Card; a rejoin locates the historical membership, reuses its `memberCardId`, and creates a new membership episode. The new/reused Card belongs to the requesting User. Resources are referenced, never copied, when the Resource feature is implemented; private study artifacts are not inherited.
- Immediate join response `201`: the member Card shape.
- If approval is enabled, joining creates only a separate PENDING `card_join_request`; it creates neither a membership nor a Card yet. Response `202`: `{ "joinRequestId": "uuid", "status": "PENDING" }`.
- A request stores `id`, `cardId`, `requestingUserId`, `inviteTokenVersion`, `status`, `createdAt`, `resolvedAt`, and `resolvedByUserId`. Request statuses are `PENDING`, `APPROVED`, `REJECTED`, and `INVALIDATED`; these are not `card_membership` statuses. `resolvedAt` and `resolvedByUserId` are null while PENDING.
- Only the Course Space Owner or an ACTIVE Admin may list, approve, or reject join requests. Approval revalidates the request's invite version and atomically creates an ACTIVE MEMBER membership referencing the created/reused member Card; response `201` is the member Card. Rejection records REJECTED and the resolver/time, creates no membership/Card, and returns `200` with `{ "status": "REJECTED" }`.
- Reset invalidates old-version PENDING requests (AD-062/069); they cannot be approved. A new join request must use the current link.

### Direct invitations (AD-032, AD-060, AD-067, AD-068, AD-070)

```http
POST   /v1/cards/{cardId}/invitations
GET    /v1/me/invitations
GET    /v1/me/invitations/{membershipId}
POST   /v1/me/invitations/{membershipId}/accept
POST   /v1/me/invitations/{membershipId}/decline
DELETE /v1/cards/{cardId}/invitations/{membershipId}
```

- Owner or ACTIVE Admin may invite an existing User with `{ "userId": "uuid" }`. A new episode responds `201` with `{ "membershipId": "uuid", "userId": "uuid", "status": "INVITED", "role": "MEMBER" }`; an existing ACTIVE/INVITED membership responds `200` with its current membership representation unchanged. It references the invitee's created-or-reused member Card (AD-067/070). No duplicate membership, Card, or event is created for the idempotent case (AD-060/070).
- Only the addressed invitee may view or respond to an invitation. Acceptance revalidates live persisted state and that the Course Space remains shared, then atomically changes `INVITED` to `ACTIVE`; decline changes it to `LEFT`. The same member Card is retained. INVITED grants no Course-Space access; acceptance grants only ordinary ACTIVE-member access.
- The invitee's list endpoint returns only their own outstanding direct invitations; the item endpoint returns only the addressed invitation. Both expose the Course Space Card ID, membership ID, status, role, and creation time. Accept and decline return `200` with the updated membership representation; withdrawal returns `200` with `{ "status": "REMOVED" }`.
- Only the Owner may withdraw a pending invitation. Withdrawal changes `INVITED` to `REMOVED`; it grants no access and retains the episode/history. Admin withdrawal is forbidden (AD-068/070).
- Invitation, acceptance, decline, and withdrawal emit `MEMBER_INVITED`, `MEMBER_INVITATION_ACCEPTED`, `MEMBER_INVITATION_DECLINED`, and `MEMBER_INVITATION_WITHDRAWN` respectively. Events are string-backed (AD-026/070).
- These endpoints are distinct from `POST /v1/join/{shareToken}`, the public invite-link flow.

### Membership (AD-023, AD-032, AD-034, AD-060, AD-067, AD-068)

```http
GET    /v1/cards/{cardId}/members
POST   /v1/cards/{cardId}/members/{userId}/promote
POST   /v1/cards/{cardId}/members/{userId}/demote
DELETE /v1/cards/{cardId}/members/{userId}
POST   /v1/cards/{cardId}/leave
```

- Member statuses are exactly `INVITED`, `ACTIVE`, `LEFT`, `REMOVED`; `status` and `role` are separate axes. At most one ACTIVE/INVITED membership exists per `(cardId,userId)` (AD-060).
- `card_membership` relates `cardId` to the Course Space Card, `userId` to the User, and `memberCardId` to that user's member Card. Historical LEFT/REMOVED episodes retain the Card link (AD-067).
- Any ACTIVE member may list members. Response entries: `{ "userId": "uuid", "status": "ACTIVE|LEFT|REMOVED|INVITED", "role": "OWNER|ADMIN|MEMBER", "joinedAt": "ISO-8601" }`.
- Only the Owner may promote/demote or remove a Member/Admin. Admins cannot promote, demote, or remove anyone. The Owner cannot be removed through this endpoint (AD-034/068).
- Leave is self-initiated by an ACTIVE non-Owner. Owner leave returns `409 Conflict` until the Owner first transfers ownership or dissolves the Course Space (AD-042/063).

### Ownership transfer (AD-042, AD-043, AD-061, AD-066, AD-067, AD-071)

```http
POST /v1/cards/{cardId}/transfer-ownership
```

- Request: `{ "targetUserId": "uuid" }`; only the current Owner may initiate.
- Target must be an existing ACTIVE member, revalidated at completion inside the atomic transaction (AD-061). A no-longer-eligible target returns `409 Conflict`.
- A self-transfer is rejected with `409 Conflict`.
- On success, `card.owner_id` and the target membership role become the new Owner; the previous Owner becomes ADMIN and remains ACTIVE. Exactly one ACTIVE OWNER membership matches `card.owner_id` before and after. The previous Owner may later leave normally (AD-066).
- In the same transaction, the new Owner membership's `memberCardId` becomes `cardId`. The former Owner receives a newly created personal Card owned by them, named and colored from the Course Space Card, and their membership `memberCardId` is updated to that Card. The target's previous personal Card remains theirs but is no longer referenced by the Owner membership. The former Owner's former personal member Card is not reconstructed from historical state (AD-067/071).
- Emits `OWNERSHIP_TRANSFERRED`. No transfer request/acceptance flow and no automatic/random assignment.

### Dissolution (AD-051, AD-063)

```http
DELETE /v1/cards/{cardId}/share
```

- Current Owner only. Sets `isShared=false` and deactivates all active share records for this Course Space. It never deletes artifacts, changes ownership, or touches member-private content. Historical events remain.

---
## Resources (AD-021, AD-057, AD-072, `RESOURCE_FILE_PROCESSING_SPEC.md`)

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
- Response `201`: `{ "id": "uuid", "ownerCardId": "uuid" (nullable), "ownerUserId": "uuid" (nullable), "title": "string", "originalFilename": "string", "mimeType": "string", "fileSizeBytes": "integer", "status": "READY|FAILED", "failureReason": "string|null", "extractedContent": "structured chunks with location metadata", "imageMetadata": "object", "createdAt": "ISO-8601" }`.
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
- Authorization: private-Card owner, or any `ACTIVE` Course-Space member
  for a Resource with an active AD-045 share record. Course-Space upload
  rights (Owner/Admin) do not restrict read access for other active
  members. Both the share record and membership are checked live per
  AD-056. Direct User-owned Resources are readable by that User only.
- `GET /v1/cards/{cardId}/resources` returns the Resource metadata and
  current extracted representation using the upload response shape.
- `GET /v1/resources/{resourceId}` streams the original bytes with the
  supported stored MIME type inline for READY display formats, otherwise
  as a download with `application/octet-stream` and `nosniff`. It does not return extracted
  text as authority; its access check uses the live Resource owner, active
  Resource share record, Card sharing state, and ACTIVE membership.

```
POST /v1/resources/{resourceId}/reprocess
```
- Authorization: owner of the Resource's owning Card/User.
- Response `200`: the upload response shape with the same Resource ID and
  current extracted representation.
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
- Share request: `{ "cardId": "uuid" }`. Only the artifact owner may
  create/reactivate its share, and must be an ACTIVE member of the target
  Course Space. A newly created or reactivated link returns `201`; an
  already-active link is idempotent (`200`) and emits no duplicate event.
  A successful change emits `ARTIFACT_SHARED` in that Course Space.
- 🔒 AD-044 authorization matrix, enforced exactly:

| Caller | Endpoint | Allowed? |
|---|---|---|
| Artifact owner | unshare (their own) | Always |
| Course Space Owner | force-unshare (anyone's) | Always; reason optional |
| Course Space Admin | force-unshare (anyone's) | Only with valid `reason` supplied |
| Ordinary Member | any of the above on others' content | Never — `403` |

- Force-unshare request: `{ "reason": "COPYRIGHT|PRIVACY|SAFETY|ABUSE|MALICIOUS_CONTENT|POLICY_VIOLATION|OTHER", "note": "string (optional)" }`; Owner may omit `reason`, Admin must supply a valid value (AD-074).
- 🔒 Force-unsharing never deletes the artifact or changes ownership —
  response `200` returns the artifact unchanged except its share state.
- Effect: emits `CONTENT_UNSHARED` or `CONTENT_FORCE_UNSHARED` (AD-046);
  the artifact's owner receives a notification on force-unshare, derived
  from that event, not a separate notification call.
- `CONTENT_FORCE_UNSHARED` payload always contains `reason` (nullable for
  Owner, required for Admin) and optional `note`. It affects only the
  selected Course Space share; other shares, artifact ownership, and the
  artifact row remain unchanged.
- Failed or stale unshare operations do not mutate a share or emit an event.

---

## Notes / Study Sets / Summaries (AD-029, AD-030)

```
POST /v1/cards/{cardId}/notes
GET  /v1/cards/{cardId}/notes
GET  /v1/notes/{noteId}
PATCH /v1/notes/{noteId}
DELETE /v1/notes/{noteId}
```

Note create/update fields: `{ "title": "string", "content": "string" }`.
PATCH fields are optional; omitted fields remain unchanged. Responses expose
`id`, `ownerCardId`, `ownerUserId`, `title`, `content`, `createdAt`, and
`updatedAt`. Delete returns `204 No Content`.

```http
POST   /v1/cards/{cardId}/studysets
GET    /v1/cards/{cardId}/studysets
GET    /v1/studysets/{studySetId}
PATCH  /v1/studysets/{studySetId}
DELETE /v1/studysets/{studySetId}
```

StudySet create/update fields: `{ "title": "string", "description": "string" }`.
PATCH fields are optional; omitted fields remain unchanged. Responses expose
`id`, `ownerCardId`, `ownerUserId`, `title`, `description`, `createdAt`, and
`updatedAt`. Delete returns `204 No Content`. B5 has no typed child items.

🔒 AD-030/076: one canonical entity per type, with no private/Course-Space
forks and no additional Note structure or StudySet item schema. Create on a
Card requires existing artifact-write authorization. Get/list allow the
actual owner or an authorized ACTIVE member through an active share. Update
and delete require persisted artifact ownership. Delete removes associated
share records transactionally but does not change ownership/account-deletion
semantics. Sharing reuses the generic `/share`/`/force-unshare` endpoints
above with `artifactType=note|studyset`; it never adds sharing state to the
artifact row. Summary endpoints remain future scope.

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
- 🔒 AD-075: active shares survive. The successor is the Course Space Card
  from the oldest active share record (`created_at ASC`, then record `id
  ASC`); Resource uses its actual `ResourceShare.card_id`. All secondary
  active shares remain unchanged. This covers both User-owned and deleting-
  User-Card-owned Resources. Artifacts with no active shares follow AD-049.
- If AD-051 blocks deletion, the `409` error details contain
  `courseSpaceCardIds`, identifying the active Course Space Cards the caller
  must transfer or dissolve before retrying.
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
- **2026-10-02** — Updated the B2 contract from AD-066 through AD-069:
  transfer result, member Card linkage, removal authority, and separate
  join-request lifecycle.
- **2026-10-02** — Added direct invitation endpoints and the complete
  `INVITED` membership lifecycle per AD-070.
- **2026-10-03** — Clarified transfer-time member Card links per AD-071; updated the ownership-transfer result.
