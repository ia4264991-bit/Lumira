# Lumira — Architecture Decisions

**Live decisions start here (AD-019 onward, 2026-09-12).** The retired
academic-hierarchy register (AD-001–018) follows below, kept as historical
record only — see its own banner.

Update protocol unchanged: an AD is only ever added, never silently edited
in meaning. Changes get a new dated Revision entry.

---

## NEW ARCHITECTURE — Card / Course Space / Sarah (live)

> **Architectural philosophy (2026-09-12), governs everything below:**
> "Don't over-engineer" means don't build capabilities the current product
> doesn't justify — it does **not** mean build shallow foundations. The
> target is *excellent foundations + a complete MVP + sensible
> extensibility* — not a throwaway prototype, and not enterprise
> infrastructure before there are users to justify it. Be rigorous about
> domain boundaries, ownership, authorization, privacy, data integrity, API
> contracts, persistence, event semantics, concurrency/idempotency where
> relevant, error handling, security, AI context isolation, and
> maintainability throughout. Do not build unnecessary microservices,
> distributed systems, or infrastructure scaled for load this product does
> not have. *(This refines, not replaces, `docs/CLAUDE_PROJECT_RULES.md`
> Rule 10 — see that file's Revisions log for the cross-reference.)*

### Card (personal workspace)

- **AD-019** — `Card` is the sole workspace entity in the domain model.
  There is no separate `CourseSpace` table/entity. A Course Space is a
  `Card` with sharing enabled: a boolean-equivalent sharing state plus a
  membership list. *(This re-derives the old AD-018's ownership/sharing
  separation on independent merit for the new model — not resurrected
  because it existed before; it was re-justified from scratch during the
  UX prototype work and happens to land in the same place.)*
  - **Persistence:** single `card` table (`id`, `ownerId`, `name`, `color`,
    `isShared`, `createdAt`). No `course_space` table.
  - **Backend responsibility:** one Card module/service handles both
    private Cards and Course Spaces — sharing state is a flag/relation on
    the same aggregate, not a fork in the domain model.
  - **API boundary:** `GET /v1/cards` (mine) and `GET /v1/cards?scope=shared`
    (Course Spaces view) are filtered reads over the same resource, not two
    endpoint families.
  - **Frontend:** "Cards" and "Course Spaces" tabs are two filtered views
    over one local/remote data source, as already validated in the UX
    prototype.

- **AD-020** — A Card's first-class sections are: **Resources, Notes,
  Quizzes, Flashcards, Sarah**. "Study Sets" is **not yet defined** as
  distinct from these — see Open Question OQ-1. Do not implement a
  "Study Sets" feature until that's resolved.

- **AD-021** — Resource ownership: a Resource belongs to exactly one
  `Card` (private or Course Space) or directly to a `User` (content not
  yet organized into any Card). A Course Space member's auto-created Card
  holds **references** to the origin Card's shared Resources, never
  copies — no storage duplication. *(Re-derives old AD-004's shape on
  independent merit; the actual mechanism decision old AD-016 left open
  — single discriminator vs. two nullable FKs — is still genuinely open
  and should be decided before Resource persistence is built, not
  assumed.)*

- **AD-022** — Ownership and sharing/visibility are separate concerns
  (re-derives old AD-018 on independent merit). Resources default to
  shared the moment a Card becomes a Course Space. Notes, Quizzes,
  Flashcards, and Sarah conversations default to **private**, shared only
  via an explicit per-item toggle. This is the load-bearing rule for
  everything else in this section.

### Course Space

- **AD-023** — Course Space membership has three roles: **Owner** (exactly
  one — the Card's creator, non-transferable in this MVP), **Admin**
  (zero or more), **Member** (zero or more). Admins may add Resources
  (shared by default per AD-022) and share the existing invite link. Only
  the Owner may remove members and archive/un-share the Course Space.
  *(Terminology note, added 2026-09-12: this is the same operation AD-051
  names and further defines as "dissolve" — use "dissolve" as the
  canonical term going forward. AD-023's original wording never actually
  distinguished a reversible "archive" state from a permanent one; that
  distinction is not decided either way and is not being invented here —
  if reversible archiving is ever wanted as a separate capability, it
  needs its own explicit decision, not an assumption in either direction.)*
  - **Persistence:** `card_membership` (`id`, `cardId`, `userId`, `role`,
    `joinedAt`).
  - **Open question:** Admin promotion/demotion mechanics — see OQ-3.

- **AD-024** — Course Space invite is a single, revocable HTTPS App Link
  per Course Space (explicitly not Firebase Dynamic Links). Resetting it
  invalidates the old one immediately; no time-based expiry. Owner and
  Admins may both view/share the current link.
  - **Persistence:** `shareToken` (or similar) column on `card`,
    regenerated on reset.
  - **API boundary:** `POST /v1/cards/{id}/share-link` (generate/reset).

- **AD-025** — Joining via the invite link is **open by default**; the
  Owner may enable "require approval," queuing join requests instead of
  instant join. Joining **auto-creates a new `Card`** for the joiner
  (`role=Member`), populated with Resource references to the origin's
  currently-shared Resources. The joiner's Notes/Quizzes/Flashcards/Sarah
  conversation start **empty** — never inherited from the origin or from
  any other member. This is AD-022 applied at the moment of joining, and
  is the single most important privacy guarantee in the whole model.
  - **Open question:** what happens to this auto-created Card on leave/
    removal/un-share — see OQ-2. **Not yet decided; do not build the
    "leave" or "remove member" action until it is.**

- **AD-026** — Course Space activity is modeled as a **single append-only
  event log** per Course Space, not a separate, disconnected notification
  model. `CourseSpaceEvent` (`id`, `cardId`, `type`, `actorUserId`,
  `createdAt`, `payload` JSON). `type` is an open, string-backed category
  (same pattern as the old, independently-good `resourceType` design),
  seeded with: `RESOURCE_ADDED`, `ARTIFACT_SHARED`, `MEMBER_JOINED`,
  `MEMBER_LEFT`, `MEMBER_REMOVED`, `ANNOUNCEMENT_POSTED`. *(Extended
  2026-09-12, additively — the type list was always open/non-exhaustive by
  design, so this isn't a meaning change: `MEMBER_PROMOTED`,
  `MEMBER_DEMOTED`, `OWNERSHIP_TRANSFERRED` (AD-042), `CONTENT_UNSHARED`
  (AD-040), `CONTENT_FORCE_UNSHARED` (AD-046), and `COURSE_SPACE_DISSOLVED`
  (AD-051) are now known-needed types.)* This single log
  serves two purposes from one source of truth:
  1. Rendered directly as the human-readable **Updates feed** inside the
     Course Space.
  2. The **sole source** that per-member notifications are derived from.
  - **Persistence:** `notification` (`id`, `eventId` FK → `CourseSpaceEvent`,
    `recipientMembershipId` FK → `card_membership`, `deliveredAt`,
    `readAt`). A notification is a **delivery/read-state wrapper around an
    event** — never a freestanding fact created independently of one. This
    directly satisfies the requirement that notifications must not be an
    arbitrary standalone model disconnected from the events that produce
    them.
  - **Backend responsibility:** one event-emission path (fired wherever a
    meaningful Course Space action already happens — add resource, share
    artifact, join, leave, remove, post announcement) fans out to both the
    feed and the per-member notification rows. There is no second,
    independent "send a notification" code path.
  - **API boundary:** `GET /v1/cards/{id}/events` (the feed);
    `GET /v1/notifications` (cross-Card, per-user); actual push delivery
    (FCM or similar) is an implementation detail on top of this, correctly
    deferred to build time — see OQ-5.

### Sarah

- **AD-027** — Sarah has two entry points on every Card/Course Space,
  which must feel like the same assistant:
  1. **Workspace Sarah** — a persistent, always-visible entry point,
     grounded in the whole Card's accessible content.
  2. **Contextual Sarah** — invoked from a specific Resource, grounded in
     that Resource plus the workspace.
  *(This formalizes what was already fully decided through chat discussion
  and validated in the UX prototype — this AD is the first time it's
  actually been written into the architecture register, not a new
  decision.)*
  - **API boundary:** the existing `AskRequest` shape (see
    `API_CONTRACT.md`) needs a `cardId` field to support Workspace Sarah —
    already flagged there as a known gap.

- **AD-028** — Sarah's context retrieval must respect the exact same
  ownership/sharing boundary as the rest of the product (AD-021/AD-022
  applied to AI, not a new principle): in a Course Space, Sarah may use the
  space's shared Resources and shared artifacts, plus the current user's
  own private content — **never** another member's private Notes, Sarah
  conversations, Quizzes, or Flashcards, regardless of membership. Worth
  stating explicitly here because it's the boundary a naive future
  RAG/retrieval implementation is most likely to violate by accident (e.g.
  "embed everything in this Course Space" would break it immediately).

### Resolutions to OQ-1 through OQ-5 (2026-09-12)

- **AD-029 (resolves OQ-1)** — A Card's full first-class content set is:
  **Resources, Notes, Study Sets, Summaries, Quizzes, Flashcards, Sarah.**
  `Study Set` is a distinct, first-class artifact type — it is **not** a
  synonym for Card, and it is **not** a replacement for Course Space. This
  supersedes AD-020's incomplete list (which was missing Study Sets and
  Summaries) — AD-020 is not deleted, but treat this AD as the complete
  version.

- **AD-030 (cross-cutting — generalizes a pattern OQ-1 and OQ-4 both
  independently pointed at)** — **Artifact identity never forks by sharing
  context or by creation method.** A Quiz, Flashcard Set, Study Set,
  Summary, or Note is exactly one entity type regardless of (a) whether
  it's private or explicitly shared into a Course Space, and (b) whether a
  human or Sarah created it. No `PersonalQuiz`/`CourseSpaceQuiz`,
  `ManualFlashcard`/`AIFlashcard`, or similar type-forking. Ownership + a
  per-item shared flag (AD-022) + an optional provenance attribute (AD-036)
  are columns on one table per artifact type, never separate tables or
  types. This generalizes the pattern already established for Resources in
  AD-021.

- **AD-031 (resolves OQ-2 — membership vs. ownership)** — Course Space
  membership governs **access only, never ownership.** A user's Card and
  every personally-owned artifact on it are entirely independent of that
  user's Course Space membership status and **survive leaving or removal
  unconditionally.** Nothing is ever auto-deleted; nothing is ever
  auto-transferred to another user, including on removal by an Admin.

- **AD-032 (resolves OQ-2 — lifecycle states)** — `card_membership.status`
  is one of `INVITED`, `ACTIVE`, `LEFT`, `REMOVED`. `LEFT` (self-initiated)
  and `REMOVED` (Owner/Admin-initiated) have **identical access effects**
  (both revoke Course-Space-mediated access) and differ only in provenance,
  recorded via the corresponding `MEMBER_LEFT`/`MEMBER_REMOVED` event
  (AD-026 — no new event architecture needed, these types were already
  seeded). Removal never grants the remover ownership of anything the
  removed user owned.

- **AD-033 (resolves OQ-2 — rejoin)** — Rejoining a Course Space creates a
  **new `card_membership` row** (a fresh `INVITED`→`ACTIVE` lifecycle) but
  must link to the user's **existing** Card — rejoining never destroys,
  recreates, or forks a new Card or identity for a returning user.

- **AD-034 (resolves OQ-3)** — Only the **Owner** may promote a Member to
  Admin, demote an Admin to Member, remove an Admin, or remove a Member.
  Admins cannot grant or revoke Admin status for anyone. The Owner cannot
  be removed by an Admin. Exactly one Owner exists per Course Space in this
  model — **no ownership-transfer operation exists yet.** If one is
  introduced later it must be its own explicit, deliberate operation, never
  an implicit side effect of an Admin action, and no transfer
  infrastructure is being built as part of this pass. *(⚠️ The "no transfer
  infrastructure being built now" clause is superseded by AD-042 below,
  2026-09-12, once OQ-7 required a minimal transfer operation as part of
  the Owner-leave lifecycle. Every other rule in this AD is unchanged and
  still in force.)*

- **AD-035 (resolves OQ-4 — MVP scope)** — Sarah-driven generation of
  Summaries, Flashcards, Quizzes, and Study Sets is **in scope for the
  MVP** — not deferred on account of involving AI. Cost and access are
  controlled through backend architecture (AD-037/AD-038), not by
  withholding the feature.

- **AD-036 (resolves OQ-4 — generated artifacts)** — A Sarah-generated
  artifact is stored as an ordinary row of that artifact's normal type —
  the same tables AD-030 establishes for manually created ones. Generation
  method is at most a provenance attribute, never a distinct entity type.
  Generated artifacts are immediately editable, studyable, and explicitly
  shareable exactly like manually created ones.

- **AD-037 (resolves OQ-4 — AI architecture/authority)** — The Android
  client holds no model-provider credentials and performs no provider
  selection — this reaffirms the frontend's existing thin-client principle
  (`frontend/README.md`), now made explicit for generation specifically,
  not only Q&A. Request flow: `User → Sarah → Backend AI Router → Context
  Builder (authorized retrieval per AD-028) → Model Provider → generated
  artifact persisted to the Card`. **AI usage metering and limit
  enforcement are server-authoritative** — the Android client is never
  trusted to calculate or enforce its own usage limits. The AI Usage meter
  UI (already prototyped) must read a backend-computed value, not a
  client-side count.

- **AD-038 (resolves OQ-4 — extension points)** — The backend AI Router is
  designed as a routing/abstraction layer from the outset — mirroring the
  existing `StorageService` abstraction pattern already noted in
  `storage/package-info.java` — specifically so free/premium allowance
  tiers, multiple model providers, and additional AI capabilities can be
  added later without a domain rewrite. **No payment/subscription system is
  built as part of this architecture pass.**

- **AD-039 (confirms OQ-5 — no change needed)** — AD-026's event-sourced
  notification model is confirmed correct as originally designed; nothing
  about it changes. Push-provider/badge/delivery specifics remain
  implementation detail, correctly deferred to build time, not an
  architectural gap.

### Resolutions to OQ-6 and OQ-7 (2026-09-12)

- **AD-040 (resolves OQ-6)** — Sharing survives the sharer's departure.
  Once a Resource or artifact is explicitly shared into a Course Space,
  its continued visibility there is governed by the **Course Space's own
  sharing record**, not by the sharer's continued membership. The sharer
  keeps ownership; loses Course Space access on leaving; does **not**
  automatically regain access merely by owning content still shared there.
  Withdrawing previously shared content requires an explicit
  unshare/remove-from-Course-Space operation — see OQ-8 for who exactly
  may invoke it, which this decision doesn't fully specify.

- **AD-041 (formalizes the separation OQ-6 and OQ-7 both depend on)** —
  Four axes must never be conflated: **Ownership** (who owns a Card/
  artifact — AD-021/AD-031), **Sharing** (which of an owner's artifacts are
  exposed into a Course Space — AD-022, AD-040), **Membership** (who
  currently has Course Space access, and its lifecycle state — AD-032),
  and **Administration** (which members hold Owner/Admin authority —
  AD-023/AD-034). A change on one axis must never implicitly change
  another: losing membership doesn't touch ownership; owning content
  doesn't grant membership; holding Admin authority doesn't grant
  ownership of anyone else's content.

- **AD-042 (resolves OQ-7 — supersedes AD-034's "no transfer" clause)** —
  The Owner **cannot leave while still Owner.** Ownership transfer is a
  required, minimal operation: only the current Owner may initiate it,
  targeting exactly one specific, already-existing eligible member (no
  self-transfer, no automatic or random assignment); it must be explicit
  and auditable as a `OWNERSHIP_TRANSFERRED` event (AD-026's event log,
  extending its seeded type list). On successful transfer, the new member
  becomes Owner and the previous Owner's role becomes Admin or Member per
  the transfer operation's own specification. Only **after** a successful
  transfer may the previous Owner leave via the ordinary `LEFT` pathway
  (AD-032). There is exactly one Owner at all times — an ownerless Course
  Space is never permitted, and ownership is never auto-assigned.

- **AD-043 (keep-it-minimal boundary on AD-042)** — The transfer operation
  is intentionally a single atomic action ("transfer ownership to member
  X") — no multi-step approval workflow, no transfer requests/invitations,
  no voting or bidding. Nothing beyond this is built for the MVP.

### Resolution to OQ-8 (2026-09-12)

- **AD-044 (resolves OQ-8 — authorization)** — The **artifact owner** may
  unshare their own artifact from a Course Space at any time, regardless
  of their Course Space role. The **Course Space Owner** may
  force-unshare any shared artifact. **Course Space Admins** may
  force-unshare a shared artifact, but only for a required, structured
  moderation reason (AD-047) — Admin force-unshare authority is
  conditioned on stating why; the Owner's is not. **Ordinary Members may
  not** withdraw content they don't own. Force-unsharing (by Owner or
  Admin) never deletes the artifact, never changes its ownership, and only
  removes it from that one Course Space — the artifact continues to exist,
  fully intact, in its owner's own Card (see AD-045 for why this is
  mechanically true, not just a stated intention). Force-unshare authority
  grants **nothing beyond** removing the share link — never a right to
  edit, delete, transfer, or claim the artifact itself.

- **AD-045 (mechanism — clarifies, does not contradict, AD-022)** —
  "Sharing" is modeled as an **explicit share record** linking an
  artifact to the Course Space it's exposed through, not a same-row
  boolean flag on the artifact itself. This is the same reference-based
  approach AD-021/AD-025 already use for Resources, now made explicit as
  the general mechanism for every shareable artifact type. It's also what
  makes AD-040's own phrase — "the Course Space's own sharing record" —
  and AD-044's "removes it from that Course Space only, artifact remains
  in owner's Card" mechanically coherent: unsharing or force-unsharing
  deletes/deactivates the share record, and never touches the artifact's
  own row. AD-022's product-level statement (defaults, per-item toggle) is
  unchanged — this is the persistence mechanism underneath it, not a
  revision of it.

- **AD-046 (resolves OQ-8 — distinguishability and events)** — Two
  distinguishable event types extend the existing `CourseSpaceEvent` log
  (AD-026): `CONTENT_UNSHARED` (owner-initiated, no reason required) and
  `CONTENT_FORCE_UNSHARED` (Owner/Admin-initiated, reason required). Every
  such event records actor, Course Space, affected artifact, operation
  type, and timestamp — all already-standard `CourseSpaceEvent` fields —
  plus, for `CONTENT_FORCE_UNSHARED` specifically, a structured moderation
  reason and optional free-text note (AD-047) in the event's `payload`.
  The artifact's owner receives a notification when their content is
  force-unshared — this is an ordinary consequence of AD-026's existing
  "notifications derive from events" design, not a new notification
  mechanism.

- **AD-047 (resolves OQ-8 — moderation reason taxonomy)** — Moderation
  reason is an open, string-backed category (the same extensibility
  pattern already used for `resourceType` and event `type`), seeded with:
  `COPYRIGHT`, `PRIVACY`, `SAFETY`, `ABUSE`, `MALICIOUS_CONTENT`,
  `POLICY_VIOLATION`, `OTHER`, with an optional accompanying free-text
  note. Required on `CONTENT_FORCE_UNSHARED`; not applicable to ordinary
  owner-initiated `CONTENT_UNSHARED`.

### Resolutions from external red-team adjudication (2026-09-12)

**AD-019 reviewed, not reopened.** Verified against every AD that depends
on it (AD-021, AD-025, AD-031, AD-033, AD-040, AD-045). No genuine
domain/security contradiction found — the Card-as-Course-Space model
remains internally coherent, and the external proposal to split
`CourseSpace` into a distinct aggregate restates a design already
considered and rejected earlier in this project (the same over-structuring
pattern caught once for the original `Workspace` entity), without new
evidence specific to a validated Lumira need. **AD-019 stands unchanged.**
See Revisions below for the full record of this review.

- **AD-048 (Card multiplicity — rejects an external proposal)** — A User
  may own **zero or more** personal Cards; each Card has exactly one owner
  (AD-021's User-ownership branch). There is **no** "exactly one primary
  Card per user" constraint. This explicitly rejects an external red-team
  proposal that would have contradicted the validated UX prototype's
  repeatable Create-Card flow and its own multi-Card examples ("Operating
  Systems," "Database Systems," "My AI Notes" coexisting for one user).
  Nothing about AD-019's persistence model (`ownerId` as a plain per-row
  reference) ever actually constrained cardinality — this AD makes
  explicit what was already structurally true rather than changing
  anything.

- **AD-049 (account deletion — private data)** — On account deletion, all
  of a User's private (non-shared) data is permanently deleted: every
  personal Card the user owns in full — including all private artifacts
  (Resources, Notes, Study Sets, Summaries, Quizzes, Flashcards) not
  currently exposed via an active share record — Sarah's private
  conversation/session history and any private derived AI data (e.g.
  embeddings) tied to that history, and all active authentication
  sessions. This is the default; the one carve-out (artifacts currently
  shared into an active Course Space) is defined explicitly in AD-050, not
  silently assumed. *(Note: AD-022 permits Sarah conversations to be
  explicitly shared via the same per-item toggle as other artifacts — never
  actually built or exercised anywhere, but if it ever is, a shared Sarah
  conversation follows AD-050's rule below like any other shared artifact,
  not this AD's default-delete rule. Flagging for consistency; not deciding
  a new feature.)*

- **AD-050 (account deletion — shared artifact survival mechanism)** — An
  artifact — used here in AD-022's sense (Resources, Notes, Study Sets,
  Summaries, Quizzes, Flashcards, **and Sarah conversations**, since AD-022
  already treats Sarah conversations as subject to the same private/shared
  toggle as everything else) — currently linked by an active share record
  (AD-045) into at least one Course Space is **not** deleted merely
  because its owner's account is deleted — doing so would silently violate
  AD-040's "sharing survives departure" guarantee, and would let a shared
  Sarah conversation fall through the deletion policy specifically.
  Resolution: at the moment of account
  deletion, such an artifact's owning-Card reference (AD-021) is
  **reassigned to the Course Space's own Card** (i.e., the current Course
  Space Owner's Card) — never to a placeholder/sentinel identity. This
  reuses the existing Card-ownership mechanism rather than inventing a new
  identity concept, and keeps ownership always attributable to a real,
  live Card, consistent with AD-041. Applies only to artifacts with an
  active share record at the moment of deletion; a deleting user is never
  the Course Space's sole Owner at this point (AD-051 guarantees that
  precondition already), so the reassignment target always belongs to
  someone else.

- **AD-051 (account deletion — Course Space ownership precondition)** — A
  User may not complete account deletion while they remain the sole Owner
  of any active Course Space. Before deletion can proceed, the user must
  either explicitly transfer ownership (AD-042/AD-043) to an eligible
  existing member, or explicitly dissolve the Course Space. There is never
  an ownerless active Course Space (reaffirms AD-042). If dissolved with
  other active members remaining, dissolution ends only collaborative
  state (membership, shared visibility) — it never touches any member's
  own personally-owned content, including the deleting Owner's own Card
  (AD-031).

- **AD-052 (account deletion — event/audit history retention)** —
  Historical `CourseSpaceEvent` records (AD-026) referencing a deleted
  user as `actorUserId` are **retained**, never deleted or rewritten, for
  Course Space auditability. The exact representation of a reference to a
  now-deleted identity is an implementation detail, not a domain decision
  — the domain rule is only: retain the event, minimize retained personal
  information beyond what auditability requires, never cascade-delete a
  Course Space's event log because one past actor's account was later
  deleted. *(Extends AD-026's event types additively with
  `COURSE_SPACE_DISSOLVED`, needed for AD-051.)*

- **AD-053 (Quiz/Flashcard — canonical artifact vs. personal study
  state)** — A canonical artifact (a Quiz or Flashcard Set — AD-029/030) is
  conceptually distinct from a user's personal execution/progress state
  against it. At minimum, the domain must support: `Quiz` (canonical
  structure/content) vs. a per-user `QuizAttempt` (one user's attempt/
  answers/score against a specific Quiz); `FlashcardSet` (canonical
  content) vs. per-user flashcard progress/review state. **This does not
  reopen AD-030** — there is still exactly one Quiz artifact regardless of
  context; only the attempt/progress records are user-specific and
  separate from it. Exact schema (e.g. how an attempt binds to a specific
  content version, review-state algorithm) is explicitly deferred — only
  the domain boundary itself (canonical artifact ≠ personal state) must
  exist before Quiz/Flashcard implementation begins.

- **AD-054 (Sarah — authorization before retrieval)** — Sarah's
  context-retrieval pipeline must determine what the requesting user is
  authorized to access **before** any content enters retrieval — never
  retrieve first and filter or rely on the model afterward. This formalizes
  AD-028's boundary with an explicit sequencing requirement AD-028 didn't
  spell out on its own: authorization is a precondition of retrieval, not
  a post-hoc filter, and the LLM is never responsible for enforcing it.

- **AD-055 (Sarah — output is untrusted)** — Sarah-generated output must be
  treated as untrusted input, the same category as any other
  unauthenticated data source. Before persistence as a Lumira artifact
  (AD-036) or use as domain data, output must pass structural/schema
  validation, domain validation, and security/input validation. A
  generated result never bypasses ordinary domain rules merely because
  Sarah produced it. Formalizes and extends AD-036/AD-037. Specific
  validator implementations are out of scope for this AD.

- **AD-056 (domain truth and deterministic authorization)** — Authoritative
  domain state (the backend's own persisted records) determines Lumira's
  domain truth and all authorization decisions. Derived/downstream systems
  — caches, search/vector indexes, object storage metadata, queues, AI
  providers, client-side state — may support the system operationally but
  must **never** independently grant or expand access beyond what current
  domain state permits. Authorization must be evaluated by backend/domain
  logic against **live** state, never inferred from: LLM output,
  client-supplied claims, cached permissions, stale membership data, or
  the mere existence/ID of an object (guards against BOLA/IDOR-shaped
  bugs). This is the general, foundational principle that AD-021/AD-028/
  AD-032/AD-041/AD-044 are all specific instances of — it replaces none of
  them. Specific enforcement mechanisms (a security framework, a
  policy-service pattern, a cache-invalidation strategy) are deferred to
  engineering/architecture documentation, not frozen here.

### Deliberately left as an extension point, not designed now

- **Offline/download capability** — the original design doc's distinction
  (available-in-Course-Space / stored-remotely / downloaded-locally) is
  the right shape conceptually, but no entity/API design is committed
  here. Per the project's own under-build-first discipline, this is
  correctly deferred until real usage shows it's needed — noted so it
  isn't silently dropped, not because it's ready to build.

---

## Open questions

### Resolved 2026-09-12 (first pass)

- ~~OQ-1 — What is a "Study Set"?~~ **Resolved by AD-029.**
- ~~OQ-2 — Membership lifecycle.~~ **Resolved by AD-031, AD-032, AD-033.**
- ~~OQ-3 — Admin promotion/demotion.~~ **Resolved by AD-034.**
- ~~OQ-4 — Quiz/Flashcard generation timing.~~ **Resolved by AD-035,
  AD-036, AD-037, AD-038.**
- ~~OQ-5 — Notification delivery mechanics.~~ **Confirmed correct,
  unchanged, by AD-039.**

### Resolved 2026-09-12 (second pass)

- ~~OQ-6 — Does content survive the sharer leaving?~~ **Resolved by
  AD-040: yes, sharing state governs visibility, independent of the
  sharer's membership.**
- ~~OQ-7 — Can the Owner leave?~~ **Resolved by AD-042/AD-043: only after
  an explicit ownership transfer. No ownerless Course Space, ever.**

### Resolved 2026-09-12 (third pass)

- ~~OQ-8 — Who may withdraw someone else's shared content?~~ **Resolved by
  AD-044/AD-046/AD-047: artifact owner unshares their own; Course Space
  Owner may force-unshare anything; Admins may force-unshare only with a
  required, structured moderation reason; ordinary Members cannot
  withdraw others' content.**

**No new open question surfaced this pass** — the resolution was complete
enough on its own terms that it didn't reveal a further gap the way each
of the previous three passes did.

---

## Revisions (new architecture)

- 2026-09-12 — AD-019 through AD-028 added: first formal architecture pass
  for the Card/Course Space/Sarah model, following the retirement of the
  old academic-hierarchy register. Five open questions flagged rather than
  decided unilaterally.
- 2026-09-12 — AD-029 through AD-039 added, resolving OQ-1 through OQ-5 per
  explicit product-owner decisions. Two new gaps (OQ-6, OQ-7) surfaced
  during incorporation and flagged rather than silently resolved.
- 2026-09-12 — AD-040 through AD-043 added, resolving OQ-6 and OQ-7 per
  explicit product-owner decisions. This pass superseded one clause of
  AD-034 (its "no transfer infrastructure being built" statement) — AD-034
  itself was not rewritten; the supersession is noted inline on AD-034 and
  recorded here. One new gap (OQ-8) surfaced and flagged rather than
  decided unilaterally.
- 2026-09-12 — AD-044 through AD-047 added, resolving OQ-8 per explicit
  product-owner decisions. AD-045 clarifies (does not contradict) AD-022's
  mechanism. No new gap surfaced this pass.
- 2026-09-12 — External red-team review (ChatGPT + Gemini) adjudicated.
  **AD-019 reviewed against its full dependency chain (AD-021, AD-025,
  AD-031, AD-033, AD-040, AD-045) and reaffirmed unchanged** — no genuine
  contradiction found; the proposed distinct-`CourseSpace`-aggregate
  restates a design already considered and rejected earlier in this
  project, without new evidence. AD-048 through AD-056 added, resolving
  five other explicitly-approved items (Card multiplicity, account
  deletion, Quiz/Flashcard study state, Sarah security invariants, domain
  truth/deterministic authorization). AD-026 extended additively (again)
  with `COURSE_SPACE_DISSOLVED`. Everything else in the external review
  not covered by these nine ADs remains unadjudicated review input, not
  architecture — per explicit instruction, no other open question was
  reopened or resolved in this pass.

---

# RETIRED — Old Academic-Hierarchy Architecture (historical record only)

> ⚠️ Everything below governed the old academic-hierarchy architecture
> (University → Semester → Course → CourseOffering → Resource). **None of
> it is authoritative.** Preserved for the reasoning trail only — see the
> live section above for current decisions.

## Retired: Chapter 3 — University Hierarchy

- **AD-001** — University is a lightweight, descriptive entity, **not** a
  data-isolation boundary in v1. Course-level organization, search, and
  sharing/visibility are scoped according to the applicable CourseOffering
  and membership relationships, not University.
- **AD-002** — Course (permanent, university-scoped catalog entry) and
  CourseOffering (a specific term's delivery) are separate entities, not one
  Semester-owned tree.
- **AD-003** — `Course.prerequisites` is free text in v1, not a structured
  Course-to-Course relationship.
- **AD-004** — Resource ownership is polymorphic: a Resource is owned by
  EITHER a CourseOffering OR a User directly (personal resource). No
  `Workspace` or `CourseSpace` entity owns Resources.
- **AD-005** — Semester is scoped to University, not global.
- **AD-006** — A CourseOffering's Course and Semester must belong to the same
  University — enforced at both the application layer and the database layer.
- **AD-017** — `CourseOfferingMembership` is a real, permanent entity
  representing academic relationship (student/instructor/TA) to a
  CourseOffering.

## Retired: Chapter 4 — Resources

- **AD-007** — Resource (stable identity) and ResourceVersion (one upload's
  actual content) are separate entities.
- **AD-008** — `Resource.currentVersionId` is denormalized.
- **AD-009** — `resourceType` is an open, string-backed category.
- **AD-010** — `sourceKind` and `resourceType` are orthogonal axes.
- **AD-011** — ResourceVersion stores a SHA-256 checksum of raw bytes.
- **AD-012** — `pageCount` is a real column; other attributes live in a
  `metadata` JSON column.
- **AD-013** — Tags are free-form strings in v1.
- **AD-014** — Uploads are async/queued. *(Was already reopened/rescoped
  before this retirement.)*
- **AD-015** — Uploads use a two-step pre-signed-URL flow. *(Was already
  reopened/revised before this retirement.)*
- **AD-016** — Polymorphic Resource ownership via two nullable FK columns +
  a database CHECK constraint.
- **AD-018** — Resource ownership and sharing/visibility are separate
  concerns. *(Re-derived independently as AD-022 above.)*

## Retired: Future Extension Points

- **CourseSpace** and **Community** extension-trigger notes — written
  against the old CourseOffering-anchored model, superseded by AD-019
  above.

---

## Revisions (retired register — historical)

- 2026-09-XX — Reversed an earlier draft's recommendation to skip
  `CourseOfferingMembership`; formalized as AD-017.
- 2026-09-XX — AD-014 reopened/rescoped.
- 2026-09-XX — AD-015 reopened/revised.
- 2026-09-12 — Entire register retired; live architecture begins above.
