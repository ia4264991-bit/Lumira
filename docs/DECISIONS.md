# Lumira — Architecture Decisions

**Live decisions start here (AD-019 onward, 2026-09-12).** The retired
academic-hierarchy register (AD-001–018) follows below, kept as historical
record only — see its own banner.

Update protocol unchanged: an AD is only ever added, never silently edited
in meaning. Changes get a new dated Revision entry.

---

## NEW ARCHITECTURE — Card / Course Space / Sarah (live)

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
  `MEMBER_LEFT`, `MEMBER_REMOVED`, `ANNOUNCEMENT_POSTED`. This single log
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

### Deliberately left as an extension point, not designed now

- **Offline/download capability** — the original design doc's distinction
  (available-in-Course-Space / stored-remotely / downloaded-locally) is
  the right shape conceptually, but no entity/API design is committed
  here. Per the project's own under-build-first discipline, this is
  correctly deferred until real usage shows it's needed — noted so it
  isn't silently dropped, not because it's ready to build.

---

## Open questions — flagged for approval, not decided here

- **OQ-1 — What is a "Study Set"?** Distinct artifact type, or does it mean
  something else (a bundle/collection, or a synonym for the Card itself as
  in Studley)? Blocks AD-020 from being complete.
- **OQ-2 — Membership lifecycle.** What happens to a member's auto-created
  Card when they leave, are removed, or the Owner un-shares the Course
  Space back to private? No precedent decided anywhere in this project's
  history. Blocks building "leave" / "remove member" / "un-share."
- **OQ-3 — Admin promotion/demotion.** Who can make a Member an Admin, and
  can an Admin be demoted, by whom?
- **OQ-4 — Quiz/Flashcard generation timing.** Earlier project history
  deferred AI generation "until Sarah exists." Sarah is now a first-class
  Card capability from day one in this architecture (AD-027). Does
  generation come back into MVP scope, or does it stay deferred for a
  different reason (cost, per the AI Usage meter concern already on
  record)? This is a product scope call, not a technical one — flagging
  rather than assuming either answer.
- **OQ-5 — Notification delivery mechanics** (push service integration,
  badge counts, read/unread UI behavior) — correctly an implementation
  detail on top of AD-026's event model, not a blocking architectural gap.
  Noted so it isn't mistaken for one.

---

## Revisions (new architecture)

- 2026-09-12 — AD-019 through AD-028 added: first formal architecture pass
  for the Card/Course Space/Sarah model, following the retirement of the
  old academic-hierarchy register. Five open questions flagged rather than
  decided unilaterally.

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
