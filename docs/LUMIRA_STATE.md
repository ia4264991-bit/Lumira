# Lumira — Project State

**This file is a compact snapshot, not a specification.** For full detail see
`docs/DECISIONS.md` (frozen architecture), `API_CONTRACT.md` (network
boundary), and `Lumira.pdf` / LPTS chapters (full product vision). If any
chat's own memory conflicts with what's in this repo, **the repo wins** — see
`CLAUDE_PROJECT_RULES.md`.

**This file describes status, not architecture.** It never overrides the LPTS
chapters or `docs/DECISIONS.md`. If a sentence here ever reads like it's
making an architectural decision rather than reporting current state, that's
a bug in this file — fix the wording here, don't treat it as ground truth.
See `CLAUDE_PROJECT_RULES.md` §0 for the full document hierarchy.

---

## What Lumira is

A client-server academic platform: Android/Kotlin/Compose frontend, Spring
Boot backend, PostgreSQL. Long-term product direction: academic organization
+ course spaces + resource management + sharing + notes/highlights/bookmarks
+ flashcards/quizzes + Sarah (AI tutor) + RAG + notifications + academic
communities. Conceptually: Google Classroom + Notion + Quizlet + an AI tutor,
with a future social layer. Designed to eventually extend beyond universities
(e.g. SHS/high school) — avoid hard-coding "university"/"semester" vocabulary
into concepts that don't need it.

## Repository

`github.com/ia4264991-bit/Lumira`, `master` branch, monorepo:
`backend/`, `frontend/`, root-level shared docs.

## Current state (as of this document)

| Area | Status |
|---|---|
| Backend Step 1 (Spring Boot skeleton) | **Done, committed, pushed.** |
| Backend Step 2 (Postgres/JPA/Flyway/Docker Compose, 11 module boundaries) | **Done, committed, pushed.** |
| Backend Step 3 (Chapter 3/4 entities) | **In progress, being rebuilt as of 2026-09-07 — see ⚠️ verification note below.** Field-by-field spec is in `docs/DECISIONS.md`. Includes `CourseOfferingMembership` (AD-017), which an earlier draft had recommended skipping — that recommendation was reversed. |
| Backend Steps 4–7 (repositories, controllers, upload, Android integration) | Not started. |
| Frontend | Fully built ahead of backend: PDF library/reader, Selection Engine (5 milestones complete, see `frontend/docs/engineering/`), Room persistence, auth screens, chat UI — all wired against `/v1/auth/*` and `/v1/ai/ask`, which don't exist on the backend yet. `DebugAuthBypass` exists specifically to work around this. Package name is still `com.aipdfreader.app` (pre-rebrand); the rename is a deliberately deferred, separate change. |
| CourseSpace / Community architecture question | **Resolved — deferred, not built.** See `docs/DECISIONS.md` Future Extension Points. The only frozen principle is AD-018 (ownership ≠ sharing/visibility) — the sharing mechanism, schema, and target model are undecided and belong to the future Sharing chapter. |

### ⚠️ Verification gap — read this before trusting any test claim from 2026-09-07 onward

Backend Step 3 work starting 2026-09-07 was written in the Architecture chat's
own sandbox, which **cannot reach Maven Central**
(`repo.maven.apache.org` → `403 host_not_allowed`, confirmed directly). This
means anything from this period marked "tests pass" or "verified" means
**manually reviewed line-by-line only — never actually compiled or executed.**
See `docs/CLAUDE_PROJECT_RULES.md` Revisions, 2026-09-07 entry, for the full
disclosure. **The first thing any session with real Maven access (e.g.
Claude Code) should do is run `mvn compile && mvn test` against everything
committed under this exception**, not assume it already passed.

## Roadmap methodology

Draft → Vertical Slice → Validate → Freeze, one chapter (or logical group) at
a time, **not** all specs frozen before any implementation. Each backend
implementation step requires explicit approval before the next begins.

**Sequencing (current agreement):**
1. Rebuild Backend Step 3 from `docs/DECISIONS.md` (adds `CourseOfferingMembership`).
2. Steps 4–5: repositories, REST controllers for Chapters 3+4.
3. **Vertical Slice 1** = Chapters 3+4 end-to-end: Android's Library screen hitting real endpoints. Technical validation only — no real students yet.
4. Freeze 3+4. Draft Chapter 5 (Study Artifacts: manual notes/highlights/bookmarks; flashcard **schema only**, generation deferred — it depends on Sarah).
5. **Vertical Slice 2** — first real student validation, since there's finally something worth reacting to.
6. Freeze 5. Draft Processing Pipeline (Ch.6), then Sarah (Ch.7) — in that order, since Sarah's retrieval depends on how content is chunked/embedded.

## Three-chat structure

| Chat | Role | Writes code? |
|---|---|---|
| **Architecture / Integration** (this one) | Planning, audits, cross-cutting decisions, reconciling the other two chats, maintaining shared docs | No |
| **Backend** | Implements backend steps, one at a time, approval-gated | Yes — backend only |
| **Android** | Implements frontend features | Yes — frontend only |

See `CLAUDE_PROJECT_RULES.md` for the operating rules each chat follows.

## Known open items

- Frontend/backend integration is entirely unverified — nothing has ever
  actually been compiled or run in a backend sandbox (Maven Central network
  access has been unavailable in at least one prior sandbox). Verify this
  early in any new backend session.
- A GitHub PAT was pasted in plaintext in an earlier chat session and should
  be treated as rotated/invalidated — always generate a fresh one per session
  rather than reusing one that's appeared in any chat transcript.
- Frontend's `applicationId`/package rename (`com.aipdfreader.app` → Lumira's
  real namespace) is tracked but not scheduled.

## Planned but not yet designed/scheduled

Recorded here so ideas mentioned in passing don't quietly disappear — none
of these are approved for implementation, just on record as intended:

- **"Buy Course" and "Community" home-screen tabs** — initially ship as a
  "Coming Soon" state with a short description only, no real functionality.
  Cheap way to signal ambition and gauge interest before building either.
  Note: "Community" already has a home in `docs/DECISIONS.md`'s Future
  Extension Points (cross-institution/subject-level groups). "Buy Course" is
  new territory — a marketplace/commerce concept (payments, seller/buyer
  roles, pricing) not discussed anywhere else yet; deserves its own design
  pass when it's actually scheduled, not to be assumed a small extension of
  Community.
- **AI Usage meter** ("Already used / Expected use" bar) — a core, reusable
  UI pattern that should apply to *every* AI generation surface (quiz,
  flashcards, Sarah, anything later), not a one-off for a single feature.
  Directly answers the cost-per-generation concern below.
- **Quiz / Flashcard generation UI** — natural next step once Sarah exists;
  already scoped via the existing flashcard-schema-now/generation-deferred
  decision.
- **Audio Overview** (podcast-style, AI-narrated) — real roadmap item, but
  sequenced *after* Card + Course Space are validated with real students,
  not alongside initial build.
- **Video Overview** — explicitly deferred, not default scope. Text-to-video
  is currently the most expensive AI generation capability by a wide margin,
  and generated video is also a real download-size problem on the
  low-bandwidth connections this project's target market assumes. Revisit
  as a conscious, likely paid-tier-only decision once there's revenue to
  absorb the cost — not something to build just because a reference product
  has it.
- **Mastery tracking** (e.g. Unfamiliar → Learning → Familiar → Mastered,
  filterable by subtopic, per-set progress) — surfaced by the Studley
  competitive review below. Worth real consideration as a retention
  mechanic independent of sharing/Course Space — it's the "come back and
  keep studying" loop, which right now only Course Space's growth mechanics
  (§17 of the Card/Course Space design doc) provide.

## Competitive notes — Studley AI (checked 2026-09-08)

Studley is the closest direct competitor identified so far: upload PDFs/
slides/notes/video → generates flashcards, quizzes, fill-in-blank, written
tests, tutor-mode explanations, and podcast audio from one "Study Set."
Relevant findings, not yet acted on:

- **Study Set → many study formats** is genuinely close to Lumira's
  Card concept, and its zero-friction "upload once, generate immediately"
  path is the bar Card's solo experience should match or beat.
- **No real ownership/sharing boundary** — Studley is "private, then
  optionally get a shareable link," which can't cleanly support scenarios
  Lumira has already worked through (a lecturer publishing without
  transferring ownership, two groups needing separate sharing spaces within
  one course). This is where Card/Course Space's added complexity earns
  its keep — but that complexity should only appear once a user actually
  chooses to share, never before, to keep Card's solo path as simple as
  Studley's.
- **Flag — do not copy:** Studley's audio feature uses celebrity-style AI
  voices (named public figures) without their involvement. Real
  personality/publicity-rights legal exposure, independent of any AI-safety
  concern. If Audio Overview is ever built, avoid this pattern entirely.
- **Pricing/free-tier angle:** Studley's free tier is capped (one study set,
  no card required) with real usage gated behind a ~$10–13/month paid tier.
  Given this project's target market (cost-sensitive, African high schools),
  a materially more generous free tier is a real potential differentiator
  worth a deliberate pricing decision later — not just a feature gap to
  note.
