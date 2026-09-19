# Lumira — Domain Model

**Derived exclusively from `docs/DECISIONS.md` AD-019 through AD-056.**
This document consolidates already-frozen decisions into one coherent
domain specification — it does not decide anything new. Where a mechanism
is genuinely unresolved in `DECISIONS.md`, it is marked as such here, not
silently chosen. Where economics, billing, or usage-reservation concepts
would normally appear, they are deliberately absent — nothing of that kind
exists in the frozen architecture as of this document's creation.

**This document is not authoritative over `DECISIONS.md`.** If anything
here appears to contradict an AD, `DECISIONS.md` wins and this document has
a bug that needs fixing, not the reverse.

---

## 1. Purpose and Scope

Answers: what exists in Lumira's domain, what owns what, what
relationships exist, what lifecycle rules apply, and what invariants must
always hold. This is a domain specification, not an implementation guide —
it does not prescribe Java/Kotlin classes, exact table DDL beyond what an
AD already froze, or specific frameworks. Implementation detail not
already frozen in `DECISIONS.md` is marked **Deferred** below, not
invented here.

---

## 2. Domain Principles

**Architectural philosophy** (`DECISIONS.md`, live section header):
excellent foundations, a complete MVP, and sensible extensibility — not a
shallow prototype, and not infrastructure scaled for load this product
doesn't have.

**The four-axis separation (AD-041)** governs every relationship in this
model. These are never conflated:

| Axis | Question it answers | Governing AD(s) |
|---|---|---|
| **Ownership** | Who owns this Card/artifact? | AD-021, AD-031 |
| **Sharing** | Which of an owner's artifacts are exposed into a Course Space? | AD-022, AD-040, AD-045 |
| **Membership** | Who currently has Course Space access, and what's its lifecycle state? | AD-032 |
| **Administration** | Which members hold Owner/Admin authority? | AD-023, AD-034 |

A change on one axis must never implicitly change another. This single
rule is the load-bearing principle behind nearly every other decision in
this document.

---

## 3. Identity Model

**Deferred — not yet specified.** No AD defines a full `User`/identity
model. What's referenced elsewhere: a `User` exists as an ownership target
(AD-021's "or directly to a User" branch) and as `card.ownerId` (AD-019).
No AD specifies authentication mechanism, session/token architecture, or
account lifecycle beyond deletion semantics (§14 below). Per
`IMPLEMENTATION_PLAN.md`'s B1, only the minimal boundary needed to
attribute a Card to a user is currently in scope — a full identity/auth
model is explicitly deferred, not decided here.

---

## 4. Card Model

**AD-019** — `Card` is the **sole workspace entity** in the domain model.
There is no separate `CourseSpace` table or entity. A Course Space is a
Card with sharing enabled: a boolean sharing state plus a membership list
on that same row.

- **Persistence**: single `card` table (`id`, `ownerId`, `name`, `color`,
  `isShared`, `createdAt`). No `course_space` table, ever.
- **AD-048** — A User may own **zero or more** Cards. Each Card has
  exactly one owner. There is no "one primary Card per user" constraint.
- **Card is not a CourseSpace identity, and never becomes one** — `isShared`
  is a plain boolean column on the same row/type; toggling it never forks
  the entity into a different kind of thing.

---

## 5. Artifact Model

**AD-020 / AD-029** — A Card's complete first-class content set:
**Resources, Notes, Study Sets, Summaries, Quizzes, Flashcards, Sarah**
(Sarah as a capability/entry point — see §12 — not an artifact type in the
same sense as the other six).

**AD-030 (no-forking principle, cross-cutting)** — Artifact identity never
forks by:
- sharing context (private vs. explicitly shared into a Course Space), or
- creation method (manually created vs. Sarah-generated).

A `Quiz` is a `Quiz` regardless of either factor. No `PersonalQuiz`/
`CourseSpaceQuiz`, no `ManualFlashcard`/`AIFlashcard`. Ownership + a
per-item shared flag (AD-022) + an optional provenance attribute (AD-036)
are columns on one table per artifact type — never separate tables or
types.

## 6. Artifact Types

| Type | Notes |
|---|---|
| `Resource` | See §7 for the ownership mechanism (AD-057, resolved). |
| `Note` | Free-form; same ownership/sharing pattern as Resources. |
| `StudySet` | Distinct first-class artifact (AD-029) — **not** a synonym for Card, **not** a replacement for Course Space. |
| `Summary` | Same pattern. |
| `Quiz` | Canonical content only — see §14 for the separate personal-attempt model. |
| `FlashcardSet` | Canonical content only — see §14 for the separate personal-progress model. |
| Sarah conversation | Per AD-022, subject to the *same* private/shared toggle as the artifact types above — see §12/§14 for the practical implications, since this has never actually been built or exercised. |

**AD-064 (file-format neutrality)** — Different underlying file formats
(PDF, DOCX, PPTX, XLSX, CSV, TXT, image formats, and others a future
Resource/File Processing specification may add) never create different
ownership, sharing, authorization, or artifact models. Every format is
stored and governed as an ordinary `Resource` under AD-021/AD-022/AD-045/
AD-057 identically — format affects only processing/extraction strategy,
which belongs entirely to the Resource/File Processing specification.

---

## 7. Resource Ownership — Persistence Mechanism RESOLVED (AD-057)

**AD-021** establishes the *shape*: a Resource belongs to exactly one
`Card` (private or Course Space) or directly to a `User` (content not yet
organized into any Card). A Course Space member's auto-created Card holds
**references** to the origin Card's shared Resources — never copies.

**AD-057 (2026-09-12) resolves the mechanism**, applied uniformly across
every artifact type in AD-030's scope (Resource, Note, StudySet, Summary,
Quiz, FlashcardSet):

```sql
owningCardId UUID NULL REFERENCES card(id),
owningUserId UUID NULL REFERENCES app_user(id),
CHECK (num_nonnulls(owningCardId, owningUserId) = 1)
```

Two nullable foreign keys plus a database CHECK constraint — never a
single `ownerType` discriminator. The discriminator alternative was
explicitly considered and rejected: it has no real, database-enforced
foreign key (Postgres cannot natively constrain one column against two
different target tables conditionally), which would leave referential
integrity for ownership resting entirely on application code, with no
backstop — an unacceptable trade against AD-056's explicit concern that
only authoritative persisted state ever determine authorization. A shared
JPA mapping (mapped-superclass or embeddable) applies this pattern once
across all six artifact types rather than repeating it six times.

This mechanism keeps AD-050's account-deletion reassignment a trivial
single-column `UPDATE`, keeps AD-045's share records entirely orthogonal
to whichever owner column is set, and satisfies AD-030's requirement that
ownership live on the artifact's own table, never a separate one.

---

## 8. Course Space Model

A Course Space is not a separate aggregate (§4). Its distinguishing
features, all expressed on the same `card` row and its related tables:

- `card.isShared = true`
- A non-empty `card_membership` set (§9)
- An active invite link (§10)
- An event log (§13)

## 9. Membership and Roles

**AD-023** — Three roles: **Owner** (exactly one, non-transferable except
via the explicit operation in §11), **Admin** (zero or more), **Member**
(zero or more). Admins may add Resources (shared by default per AD-022)
and share the existing invite link. Only the Owner may remove members and
dissolve the Course Space (see terminology note below).

**AD-032** — `card_membership.status` ∈ `{INVITED, ACTIVE, LEFT, REMOVED}`
— a field **distinct from** `role`. `LEFT` (self-initiated) and `REMOVED`
(Owner/Admin-initiated) have identical access effects, differing only in
provenance (recorded via `MEMBER_LEFT`/`MEMBER_REMOVED` events, §13).

- **Persistence**: `card_membership` (`id`, `cardId`, `userId`, `status`,
  `role`, `joinedAt`).

**AD-034** — Only the **Owner** may promote Member→Admin, demote
Admin→Member, remove an Admin, or remove a Member. Admins cannot grant or
revoke Admin status for anyone. The Owner cannot be removed by an Admin.

**AD-025 / AD-033** — Joining is open by default (Owner may require
approval). Joining **auto-creates a new Card** for the joiner (`role =
Member`), with Resource references to the origin's currently-shared
Resources — the joiner's Notes/Quizzes/Flashcards/Sarah conversation start
**empty**, never inherited. Rejoining creates a **new** `card_membership`
row but links to the user's **existing** Card — never forks a new Card or
identity.

**AD-031** — Membership governs access only, never ownership. A user's
Card and every personally-owned artifact on it survive leaving or removal
**unconditionally**. Nothing is auto-deleted; nothing is auto-transferred.

**Terminology note (2026-09-12, `DECISIONS.md` inline)**: AD-023's
"archive/un-share the Course Space" and AD-051's "dissolve" refer to the
**same operation** — "dissolve" is the canonical term going forward.
Whether a separate, *reversible* "archive" state (distinct from permanent
dissolution) should exist was never decided and is not invented here.

**AD-060 (membership uniqueness)** — At most one `card_membership` row
with status `ACTIVE` or `INVITED` may exist for a given `(cardId,
userId)` pair at any time — enforced as a partial unique database index,
not application logic alone. Historical `LEFT`/`REMOVED` rows are
retained without limit; this invariant only prevents duplicate *current*
memberships.

**AD-063 (dissolution)** — Dissolving a Course Space deactivates all of
its active share records (AD-045) — shared content stops being visible
through it. It never deletes an artifact, never changes ownership, and
never touches any member's own private content. Historical events remain
per AD-052.

---

## 10. Invite Link

**AD-024** — A single, revocable HTTPS App Link per Course Space
(explicitly not Firebase Dynamic Links). Resetting invalidates the old
link immediately; no time-based expiry. Owner and Admins may both view/
share the current link.

- **Persistence**: `shareToken` column on `card`, regenerated on reset.

**AD-062** — Resetting the link also invalidates any join request still
pending approval that was submitted via the link being reset — the whole
point of a reset is to cut off access tied to that link, including
requests not yet actioned. A voided request must be resubmitted via the
new link.

---

## 11. Ownership Transfer

**AD-042** — The Owner **cannot leave while still Owner.** Transfer is a
required, minimal, atomic operation: only the current Owner may initiate
it, targeting exactly one specific existing eligible member (no
self-transfer, no automatic/random assignment), recorded as an
`OWNERSHIP_TRANSFERRED` event. The previous Owner's role becomes Admin or
Member per the operation's own specification. There is exactly one Owner
at all times — never ownerless, never auto-assigned.

**AD-061** — Target eligibility is revalidated **at completion**, not
only at initiation, within the same atomic transaction — if the target's
membership changed in between, the transfer fails rather than completing
against a no-longer-eligible member.

**AD-043** — Intentionally minimal: a single atomic action, no multi-step
workflow, no requests/invitations, no voting.

---

## 12. Sharing and Share Records

**AD-022** — Ownership and sharing/visibility are separate concerns.
Resources default to shared the moment a Card becomes a Course Space.
Notes, Quizzes, Flashcards, Study Sets, Summaries, and **Sarah
conversations** default to **private**, shared only via an explicit
per-item toggle.

**AD-045 (mechanism)** — Sharing is an **explicit share record** linking
an artifact to the Course Space it's exposed through — **not** a same-row
boolean flag on the artifact. This is the same reference-based approach
already used for Resources (AD-021/AD-025), generalized to every
shareable artifact type. This is what makes "sharing survives departure"
(§9/AD-040) and "force-unshare doesn't touch the artifact" (below)
mechanically true rather than merely stated.

**AD-040** — Sharing survives the sharer's departure: continued visibility
is governed by the Course Space's own share record, not by the sharer's
continued membership. The sharer keeps ownership; loses Course Space
access on leaving; does **not** regain access merely by owning content
still shared there.

**AD-044 (unshare/force-unshare authorization)**:

| Actor | Can unshare... | Condition |
|---|---|---|
| Artifact owner | Their own artifact | Always, any Course Space role |
| Course Space Owner | Any shared artifact | Always |
| Course Space Admin | Any shared artifact | Only with a required moderation reason (AD-047) |
| Ordinary Member | Nothing they don't own | Never |

Force-unsharing never deletes the artifact, never changes ownership, and
only removes it from that one Course Space — the artifact remains fully
intact on its owner's own Card. Force-unshare authority grants **nothing
beyond** removing the share link — never edit, delete, transfer, or claim
rights.

**AD-046** — Two distinguishable event types: `CONTENT_UNSHARED`
(owner-initiated, no reason required) and `CONTENT_FORCE_UNSHARED`
(Owner/Admin-initiated, reason required). The artifact's owner is
notified on force-unshare, as an ordinary consequence of the event model
(§13) — not a separate mechanism.

**AD-047** — Moderation reason is an open, string-backed category, seeded
with `COPYRIGHT`, `PRIVACY`, `SAFETY`, `ABUSE`, `MALICIOUS_CONTENT`,
`POLICY_VIOLATION`, `OTHER`, plus optional free-text note. Required on
`CONTENT_FORCE_UNSHARED` only.

---

## 13. Course Space Events and Notifications

**AD-026** — A **single append-only event log** per Course Space —
`CourseSpaceEvent` (`id`, `cardId`, `type`, `actorUserId`, `createdAt`,
`payload` JSON). `type` is open/string-backed, seeded with:
`RESOURCE_ADDED`, `ARTIFACT_SHARED`, `MEMBER_JOINED`, `MEMBER_LEFT`,
`MEMBER_REMOVED`, `ANNOUNCEMENT_POSTED`, `MEMBER_PROMOTED`,
`MEMBER_DEMOTED`, `OWNERSHIP_TRANSFERRED`, `CONTENT_UNSHARED`,
`CONTENT_FORCE_UNSHARED`, `COURSE_SPACE_DISSOLVED`.

This single log serves two purposes from one source of truth:
1. Rendered as the human-readable Updates feed.
2. The **sole source** that per-member notifications derive from.

- **Persistence**: `notification` (`id`, `eventId` FK, `recipientMembershipId`
  FK, `deliveredAt`, `readAt`) — a delivery/read-state **wrapper around an
  event**, never a freestanding fact.

**AD-039** — This model is confirmed correct as designed; push-delivery
mechanics (FCM or similar) are implementation detail, deferred, not a
domain gap.

---

## 14. Personal Study State

**AD-053** — A canonical artifact (`Quiz`, `FlashcardSet`) is conceptually
distinct from a user's personal execution/progress state against it:

```
Quiz (canonical)                    FlashcardSet (canonical)
  └── QuizAttempt (per user)          └── flashcard progress/review state (per user)
```

This does **not** reopen AD-030 — there is still exactly one `Quiz`
regardless of context; only attempt/progress records are user-specific.
**Exact schema (how an attempt binds to a specific content version, review
-state algorithm) is explicitly deferred** — only the boundary itself must
exist before B6/B7 implementation begins.

---

## 15. Sarah

**AD-027** — Two entry points, meant to feel like the same assistant:
1. **Workspace Sarah** — persistent, always-visible, grounded in the
   whole Card's accessible content.
2. **Contextual Sarah** — invoked from a specific Resource, grounded in
   that Resource plus the workspace.

**AD-028** — Sarah's context retrieval respects the same ownership/sharing
boundary as the rest of the product: in a Course Space, Sarah may use the
space's shared content plus the requester's own private content —
**never** another member's private Notes, Sarah conversations, Quizzes,
or Flashcards, regardless of membership.

**AD-054 (sequencing)** — Authorization is determined **before** any
content enters retrieval — never retrieve-then-filter, and the LLM is
never responsible for enforcing this boundary itself.

**AD-058 (extends AD-054 — authorization at every access, not just
request start)** — Authorization must remain valid **at the moment each
protected access actually occurs**, not merely at request initiation. An
async pipeline, a retry, or a follow-up retrieval the model proposes
mid-conversation each re-check live state. Retrieved or generated content
— including content that itself contains instructions — is **data,
never an authorization grant**; a tool call triggered by such content
still requires the requesting user's own independent, live authorization
for whatever it names, regardless of what the content says.

**AD-055 (output trust)** — Sarah-generated output is treated as untrusted
input. Before persistence, it must pass structural/schema validation,
domain validation, and security/input validation — a generated result
never bypasses ordinary domain rules merely because Sarah produced it.

**AD-065 (validation granularity)** — A multi-component generated
artifact (e.g. a ten-question Quiz) is validated and persisted as a
**complete whole, or not at all** — no partially-valid subset is silently
persisted if any required component fails validation. A clean failure the
user can retry is preferable to a silently-incomplete artifact.

### AI Generation and Provenance

**AD-035** — Sarah-driven generation of Summaries, Flashcards, Quizzes,
and Study Sets is **in MVP scope**, not deferred for being AI.

**AD-036** — A generated artifact is stored as an ordinary row of its
normal type (AD-030's no-forking rule applied to creation method).
Generation method is at most a provenance attribute, never a distinct
type. Immediately editable, studyable, and shareable like any manual
artifact.

**AD-059 (provenance never grants authorization)** — Provenance describes
where generated content came from; it never independently grants
ownership, membership, visibility, or authorization to anything it
references. A generated Study Set citing a private Resource as a source
does not make that Resource accessible to anyone who can see the Study
Set — access to the cited source is still governed entirely by the
source's own ownership/sharing state, independent of what's derived from
it, and independent of whether the original contributor has since left,
been removed, or been deleted.

**AD-037** — Android holds no model-provider credentials and performs no
provider selection. Flow: `User → Sarah → Backend AI Router → Context
Builder (AD-028/054) → Model Provider → generated artifact persisted to
the Card`. **Usage metering/limit enforcement is server-authoritative** —
the client never calculates or enforces its own limit; the AI Usage meter
UI must read a backend-computed value.

**AD-038** — The AI Router is an abstraction layer from the outset
(mirroring the existing `StorageService` pattern), so provider/tier
extensibility doesn't require a domain rewrite later. **No payment/
subscription system is built as part of this architecture.**

---

## 16. Account Deletion Semantics

**AD-049 (private data)** — On deletion: every personal Card the user
owns in full, including all private artifacts not currently shared, Sarah
private conversation/session history and derived AI data, and all active
sessions — **permanently deleted.**

**AD-050 (shared artifact survival)** — An artifact — including a shared
Sarah conversation, per AD-022 — currently linked by an active share
record is **not** deleted. Its owning-Card reference is **reassigned to
the Course Space's own Card** (the current Owner's Card) — never to a
placeholder/sentinel identity. The reassignment target is always someone
else's live Card, since AD-051 guarantees the deleting user is never the
Course Space's sole Owner at this point.

**AD-051 (Owner precondition)** — A User cannot complete deletion while
sole Owner of any active Course Space. Must transfer ownership (§11) or
explicitly dissolve first. Dissolution ends only collaborative state —
never touches any member's own Card, including the departing Owner's.

**AD-052 (audit retention)** — Historical `CourseSpaceEvent` records
referencing a deleted user are **retained**, never rewritten or
cascade-deleted. Exact representation of a reference to a deleted identity
is implementation detail.

```
Event                | Artifact  | Share record        | Visibility
----------------------|-----------|----------------------|---------------------------
Owner shares          | intact    | created/active       | visible to authorized members
Owner unshares        | intact    | removed              | no longer visible
Owner leaves/removed  | intact    | remains active       | remains visible (AD-040)
Owner account deleted | intact    | remains active,      | remains visible — owning
                      |           | reassigned (AD-050)  | Card changes, share does not
Force-unshared        | intact    | removed              | no longer visible
```

---

## 17. Authorization and Domain Truth

**AD-056** — Authoritative domain state (the backend's own persisted
records) determines domain truth and all authorization decisions.
Derived/downstream systems — caches, search/vector indexes, object
storage metadata, queues, AI providers, client-side state — may support
the system operationally but **never** independently grant or expand
access. Authorization is evaluated against **live** state, never
inferred from LLM output, client-supplied claims, cached permissions,
stale membership data, or an object's mere existence/ID.

This is the general principle that AD-021, AD-028, AD-032, AD-041, and
AD-044 are all specific instances of — it replaces none of them.

---

## 18. Explicitly Deferred / Unresolved

Marked here so no implementation agent infers a decision that hasn't been
made:

- Full Identity/authentication model (§3) — only a minimal boundary is in
  scope for B1.
- Exact `QuizAttempt`/flashcard-progress schema (§14).
- Offline/download capability (`DECISIONS.md`'s standing extension point).
- Resource/File Processing specification (file types, MIME validation,
  size limits, extraction, OCR, multimodal, page/region coordinates,
  spreadsheet handling, failure/retry) — a separate engineering document,
  gates B3 per `IMPLEMENTATION_PLAN.md`.
- Push-delivery mechanics for notifications (AD-039).
- Whether a reversible "archive" state distinct from permanent "dissolve"
  should exist (§9 terminology note).
- **Explicitly out of scope for the 2026-09-12 closure pass, not
  oversights:** full subscription/payment architecture (would contradict
  AD-038 without an explicit decision to amend it), full search/vector
  infrastructure, admin RBAC, async/job/queue architecture, transactional
  outbox, caching policy, observability, and a database-index catalogue.
  These remain deferred pending validated product need or a future,
  explicit decision to bring them into architectural scope — not invented
  here merely because a broader request asked for them.
- **Not present in this document because not present in `DECISIONS.md`:**
  any usage-reservation model, Study Credits, provider pricing, billing,
  or subscription economics of any kind.

---

## 19. Summary — Entity Relationship Overview

```
User (deferred identity model)
  └── owns → Card (0..N, AD-048)
                ├── isShared: boolean (AD-019) — same row, never forks type
                ├── card_membership (0..N) — status × role, independent axes
                │     status: INVITED | ACTIVE | LEFT | REMOVED (AD-032)
                │     role:   OWNER | ADMIN | MEMBER (AD-023/034)
                ├── shareToken (AD-024)
                ├── owns → Resource / Note / StudySet / Summary / Quiz /
                │          FlashcardSet / Sarah-conversation
                │            └── ShareRecord (0..1 per Course Space, AD-045)
                │                  → visible to authorized members (AD-040)
                ├── Quiz → QuizAttempt (per user, AD-053)
                ├── FlashcardSet → progress state (per user, AD-053)
                └── CourseSpaceEvent (append-only, AD-026)
                      └── Notification (delivery/read wrapper, per member)
```
