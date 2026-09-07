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
