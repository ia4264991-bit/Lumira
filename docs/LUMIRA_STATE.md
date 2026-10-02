# Lumira — Project State

> ⚠️ **OLD ARCHITECTURE RETIRED — 2026-09-12.** This file's "What Lumira is,"
> "Current state," and "Roadmap methodology" sections previously described
> the academic-hierarchy architecture and its Step 1–7 backend rollout.
> **That content has been retired** and is replaced below with the actual
> current state: a UX prototype exists as the product reference, and a new
> architecture has not yet been written around it. The "Planned but not yet
> designed" and "Competitive notes" sections below predate this retirement
> but describe the *new* direction, not the old one — they are preserved
> unchanged.

**This file describes status, not architecture.** See
`docs/CLAUDE_PROJECT_RULES.md` for how this file relates to other documents
— that relationship is itself being redefined alongside the new
architecture, not assumed to carry over unchanged from the old hierarchy.

---

## What Lumira is (current, accurate)

A study platform built around Card as its sole workspace entity, with
Course Space as the sharing/collaboration capability on a Card and Sarah as
a first-class workspace capability. The current architecture is recorded
in `docs/DECISIONS.md` (AD-019 through AD-070), consolidated in
`docs/DOMAIN_MODEL.md`, and reflected in `API_CONTRACT.md`.

## Current repository state (verified 2026-10-03)

| Area | Status |
|---|---|
| Backend | B0/B1 code exists; user-provided Maven output reports 27/27 tests passing against PostgreSQL 18.3 with V3 applied. B1 corrections remain uncommitted. B2 implementation and PostgreSQL integration tests are in the working tree but have not passed Maven validation in this sandbox. |
| Architecture | AD-066 through AD-070 define transfer, member-Card linkage, removal, join requests, and direct invitations. Transfer implementation remains gated on the AD-066/AD-067 memberCardId interaction, for which clarification has been requested. |
| Frontend | Working Kotlin/Compose PDF-reader/auth/chat thin client; it predates the Card/Course Space pivot. See `frontend/README.md`. |
| API contract | Live and authoritative at the network boundary; B2 sections extended from AD-066 through AD-070. |
| UX prototype | Standalone HTML/CSS/JS artifact exists outside this repository; it remains the product reference. |

## What's next

1. Resolve the AD-066/AD-067 `memberCardId` behavior across ownership transfer.
2. Finish B2 lifecycle/security tests and validate migrations against PostgreSQL.
3. Reconcile/commit the B1 corrections with B2, then complete and push B2.
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
