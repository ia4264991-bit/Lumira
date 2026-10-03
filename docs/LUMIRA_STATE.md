# Lumira — Project State

> ⚠️ **OLD ARCHITECTURE RETIRED — 2026-09-12.** This file's "What Lumira is,"
> "Current state," and "Roadmap methodology" sections previously described
> the academic-hierarchy architecture and its Step 1–7 backend rollout.
> **That content has been retired.** The current architecture is recorded
> in `docs/DECISIONS.md` and derived in `docs/DOMAIN_MODEL.md`; this file
> records project status only. The "Planned but not yet designed" and
> "Competitive notes" sections below remain product context, not approved
> implementation scope.

**This file describes status, not architecture.** See
`docs/CLAUDE_PROJECT_RULES.md` for how this file relates to other documents
— that relationship is itself being redefined alongside the new
architecture, not assumed to carry over unchanged from the old hierarchy.

---

## What Lumira is (current, accurate)

A study platform built around Card as its sole workspace entity, with
Course Space as the sharing/collaboration capability on a Card and Sarah as
a first-class workspace capability. The current architecture is recorded
in `docs/DECISIONS.md` (AD-019 onward), consolidated in
`docs/DOMAIN_MODEL.md`, and reflected in `API_CONTRACT.md`.

## Current repository state (verified 2026-10-03)

| Area | Status |
|---|---|
| Backend | B0-B10 are committed. B10 Sarah Generation is at `3e7adaa5ad21be9db9f63a60d337073cd2674145` and passed PostgreSQL validation: 88 tests, 0 failures, 0 errors, 0 skipped. Summary generation is deferred under AD-081. B11 Backend Integration + Security is next. |
| Architecture | AD-066 through AD-077 define transfer, membership, sharing, deterministic account-deletion ownership succession, Notes/Study Sets, and the canonical Quiz/personal QuizAttempt model. B6 reuses AD-057 ownership and AD-045 sharing. |
| Frontend | Working Kotlin/Compose PDF-reader/auth/chat thin client; it predates the Card/Course Space pivot. See `frontend/README.md`. |
| API contract | Live and authoritative at the network boundary; B2-B6 endpoint contracts are maintained in `API_CONTRACT.md`. |
| UX prototype | Standalone HTML/CSS/JS artifact exists outside this repository; it remains the product reference. |

## What's next

1. B4 Sharing + Authorization is complete at `91afb90a7d48816ee329b5b3809d2ce9c0793cdd` after the 55-test PostgreSQL suite passed.
2. B5 Notes + Study Sets is complete at `4806efdff861e7a8eb009d58edb02abb3f413d73` after the 60-test PostgreSQL suite passed; its completion record is `72d5c9d34339d617fae6d52f3f7eb02389a9a66a`.
3. B6 Quizzes, B7 Flashcards, B8 Events + Notifications, B9 Sarah Foundation, and B10 Sarah Generation are complete and validated. B10 supports FlashcardSet, Quiz, and StudySet; Summary remains deferred under AD-081. B11 Backend Integration + Security is next.
## Three-way collaboration structure

Unchanged by this retirement — this is a workflow pattern, not part of the
old domain architecture:

| Role | Scope |
|---|---|
| **Architecture/Integration** (this chat) | Planning, audits, cross-cutting decisions, maintaining shared docs. Does not implement. |
| **Backend implementation** (increasingly via Antigravity/Claude Code, not a browser chat) | Implements backend work, approval-gated. |
| **Android implementation** (Android Studio / Gemini) | Implements frontend features. |

Whether this exact structure survives the new architecture's own document
hierarchy is an open question for that upcoming work, not decided here.

## Planned but not yet designed/scheduled

Recorded here so ideas mentioned in passing don't quietly disappear — none
of these are approved for implementation, just on record as intended:

- **"Buy Course" and "Community" home-screen tabs** — initially ship as a
  "Coming Soon" state with a short description only, no real functionality.
  "Community" is a cross-institution/subject-level concept distinct from
  Course Space (different scale, discovery, moderation needs). "Buy Course"
  is a marketplace/commerce concept (payments, seller/buyer roles) not
  designed anywhere yet.
- **AI Usage meter** ("Already used / Expected use" bar) — a core, reusable
  UI pattern for every AI generation surface (quiz, flashcards, Sarah),
  already prototyped in the UX prototype.
- **Quiz / Flashcard generation UI** — depends on Sarah; schema-first,
  generation deferred until Sarah exists.
- **Audio Overview** (podcast-style, AI-narrated) — sequenced after Card +
  Course Space are validated with real students.
- **Video Overview** — explicitly deferred; most expensive AI generation
  capability by a wide margin, and a real download-size problem on the
  low-bandwidth connections this project's target market assumes. Revisit
  as a conscious, likely paid-tier-only decision once there's revenue to
  absorb the cost.
- **Mastery tracking** (Unfamiliar → Learning → Familiar → Mastered,
  filterable, per-set progress) — a retention mechanic independent of
  sharing, surfaced by the Studley competitive review below.

## Competitive notes — Studley AI (checked 2026-09-08)

Studley is the closest direct competitor identified so far: upload PDFs/
slides/notes/video → generates flashcards, quizzes, fill-in-blank, written
tests, tutor-mode explanations, and podcast audio from one "Study Set."

- **Study Set → many study formats** is genuinely close to Lumira's Card
  concept; its zero-friction "upload once, generate immediately" path is
  the bar Card's solo experience should match or beat.
- **No real ownership/sharing boundary** — Studley is "private, then
  optionally get a shareable link." Card/Course Space's added complexity
  (Owner/Admin/Member, explicit share vs. auto-share) earns its keep here —
  but should only appear once a user actually chooses to share, never
  before, to keep Card's solo path as simple as Studley's.
- **Flag — do not copy:** Studley's audio feature uses celebrity-style AI
  voices (named public figures) without their involvement — real
  personality/publicity-rights legal exposure. Avoid this pattern entirely
  if Audio Overview is ever built.
- **Pricing/free-tier angle:** Studley gates real use behind a ~$10–13/month
  paid tier. Given this project's target market, a materially more
  generous free tier is a real potential differentiator worth a deliberate
  pricing decision later.

## Known open items (unaffected by the architecture retirement)

- A GitHub PAT was pasted in plaintext in an earlier chat session and
  reused once since under an explicit, logged exception — treat any
  credential that has ever appeared in a chat as compromised regardless of
  this history; always prefer a fresh, narrowly-scoped token going forward.
- Frontend's `applicationId`/package rename (`com.aipdfreader.app` →
  Lumira's real namespace) is tracked but not scheduled.
