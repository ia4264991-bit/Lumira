# Lumira — Architecture Decisions (Frozen)

**This file is the single source of truth for frozen, numbered architecture
decisions.** The LPTS remains authoritative for domain requirements that have
not yet been crystallized into an AD. Chat transcripts, prior handoff
documents, and old summaries are NOT authoritative — if any of them conflict
with this file, this file wins.

Update protocol: an AD is only ever added here, never silently edited in
meaning. If a decision needs to change, add a new dated entry under
"Revisions" below the original AD, stating what changed and why. ADs are
immutable with respect to meaning — but a typographical or formatting
correction that cannot alter interpretation (e.g. a misspelling) may be made
in place; any change that alters what an AD actually says requires a dated
Revision entry, not an in-place edit.

**Where this file sits relative to the LPTS chapters:** this file operationalizes
the LPTS domain specification into binding, numbered decisions — it does not
stand apart from it. If an LPTS requirement and an AD appear to conflict,
implementation must **stop** — the conflict must be resolved through an
explicit AD revision or clarification before proceeding. This is not
optional or a judgment call for whichever chat encounters it. See
`CLAUDE_PROJECT_RULES.md` §0 for the full document hierarchy.

---

## Chapter 3 — University Hierarchy

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
  `Workspace` or `CourseSpace` entity owns Resources. "Personal / Shared /
  Archived" are product/UI groupings (query filters), not hierarchy nodes.
- **AD-005** — Semester is scoped to University, not global (academic
  calendars vary by institution).
- **AD-006** — A CourseOffering's Course and Semester must belong to the same
  University — enforced at both the application layer and the database layer.
- **AD-017** — `CourseOfferingMembership` (Ch.3 §2.6) is a real, permanent
  entity representing academic relationship (student/instructor/TA) to a
  CourseOffering. It is **not** superseded or replaced by any future
  collaboration/sharing concept — see the CourseSpace extension trigger below.
  *(Added after the CourseSpace review concluded this membership concept
  should be built, reversing an earlier draft recommendation to skip it.)*

## Chapter 4 — Resources

- **AD-007** — Resource (stable identity) and ResourceVersion (one upload's
  actual content) are separate entities.
- **AD-008** — `Resource.currentVersionId` is denormalized (avoids a query on
  every resource-list read).
- **AD-009** — `resourceType` is an open, string-backed category (not a closed
  DB enum), dispatched to a `ResourceProcessor` registry at the application
  layer.
- **AD-010** — `sourceKind` (UPLOADED / EXTERNAL_LINK / AI_GENERATED) and
  `resourceType` (PDF, DOCX, etc.) are orthogonal axes — origin vs. format.
- **AD-011** — ResourceVersion stores a SHA-256 checksum of raw bytes.
- **AD-012** — `pageCount` is a real column (hot path); other format-specific
  attributes live in a `metadata` JSON column.
- **AD-013** — Tags are free-form strings in v1, not a normalized Tag entity.
- **AD-014** — Uploads are NEVER processed synchronously in the request path —
  always async/queued, regardless of file size or type.
- **AD-015** — Uploads use a two-step, pre-signed-URL flow (client uploads
  directly to object storage), not routing bytes through the app server.
- **AD-016** — Polymorphic Resource ownership is two nullable FK columns
  (`courseOfferingId`, `personalOwnerId`) with a database CHECK constraint —
  not a single untyped `ownerId`.
- **AD-018** — **Resource ownership and sharing/visibility are separate
  concerns.** Sharing a Resource must never transfer or imply a change of
  `ownerType`/`ownerId` (AD-004/AD-016). This is the entire scope of this AD.
  The mechanism, schema, and target model for sharing — what a share points
  at, revocation, expiry, permissions, notifications, and so on — are
  explicitly **not** decided by this AD and are not frozen anywhere yet. They
  belong to the future Sharing chapter, to be scoped when that chapter is
  drafted. *(Added after the CourseSpace architecture review.)*

---

## Future Extension Points (not ADs — explicitly deferred, with trigger conditions)

These are recorded so future chats don't re-litigate settled reasoning, and so
premature structure isn't built for unvalidated needs. They are not
architecture yet.

- **CourseSpace** — Introduce only if validated product usage demonstrates
  that multiple independent collaboration/sharing groups are genuinely needed
  within a single CourseOffering (e.g. real requests for course sub-sections
  with separate sharing). If/when built: it organizes **membership and
  visibility only** — it must never become a `Resource` owner (would require
  reopening AD-004/AD-016, which should not happen without a full review).
- **Community** — A separate, later concept for cross-institution or
  subject-level groups (e.g. "CS students across UCC/KNUST/Legon"). Must
  **not** be modeled as an unanchored/nullable-FK variant of CourseSpace —
  it has different scale, discovery, and moderation needs and deserves its
  own chapter and entities when it becomes an active requirement.

---

## Revisions

- 2026-09-XX — Reversed an earlier draft's recommendation to skip
  `CourseOfferingMembership`; formalized as AD-017 after the CourseSpace
  review concluded the academic-relationship concept is independently needed
  regardless of the CourseSpace/Community question.
