# Lumira — Architecture Decisions

> ⚠️ **RETIRED — 2026-09-12.** Every AD below (AD-001 through AD-018) governed
> the old academic-hierarchy architecture (University → Semester → Course →
> CourseOffering → Resource). **None of these decisions are authoritative
> for the new prototype-first direction.** They are preserved below as
> historical record — the reasoning in some of them may still be useful
> (e.g. the ownership/sharing separation in AD-018 turned out to generalize
> well and was independently re-derived for the Card/Course Space model) —
> but nothing here should be cited as "already decided" for new work.
>
> A new decisions register for the prototype-first architecture will be
> created separately. This file is not being deleted, because the reasoning
> trail (including two real architecture reviews and a red-team pass) has
> genuine value — but it must not be read as current.

---

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
  before this retirement — see prior Revisions entry below.)*
- **AD-015** — Uploads use a two-step pre-signed-URL flow. *(Was already
  reopened/revised before this retirement — see prior Revisions entry
  below.)*
- **AD-016** — Polymorphic Resource ownership via two nullable FK columns +
  a database CHECK constraint.
- **AD-018** — **Resource ownership and sharing/visibility are separate
  concerns.** *(Worth flagging specifically: this principle was
  independently re-derived for the Card/Course Space model during UX
  prototyping, without reference to this AD. It may be worth carrying
  forward into the new register on its own merits — that's a decision for
  whoever builds the new register, not something this retirement notice
  should decide.)*

## Retired: Future Extension Points

- **CourseSpace** and **Community** extension-trigger notes — both written
  against the old CourseOffering-anchored model. The Card/Course Space UX
  prototype has since materially superseded this framing (Course Space is
  now "a Card with sharing enabled," not a CourseOffering-anchored
  membership entity) — these notes are retired, not carried forward as-is.

---

## Revisions (historical — record of what happened while this register was live)

- 2026-09-XX — Reversed an earlier draft's recommendation to skip
  `CourseOfferingMembership`; formalized as AD-017.
- 2026-09-XX — AD-014 reopened/rescoped: synchronous/local upload handling
  permitted for the then-current milestone; async/queued moved to a future
  target.
- 2026-09-XX — AD-015 reopened/revised: local file storage via the app
  server was the then-current design; pre-signed-URL flow moved to a future
  target.
- **2026-09-12** — Entire register retired. See banner above.
