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

A study platform being rebuilt around a validated UX prototype: **Card**
(private study workspace: Resources, Notes, AI/Sarah, Summaries, Quizzes,
Flashcards) with **Course Space** as its sharing/collaboration layer (same
underlying Card, membership + sharing enabled — not a separate entity).
Sarah is a first-class, embedded workspace capability, not a bolt-on
chatbot. Target market includes African high schools — mobile-first,
offline/low-bandwidth-friendly, and cost-sensitive infrastructure
considerations apply.

**A formal architecture for this has not been written yet.** The UX
prototype (an HTML/CSS/JS click-through, not committed to this repository)
is the product/UX reference point. Building the architecture around it is
the next task, not something this document should pre-empt.

## Current repository state (accurate as of 2026-09-12)

| Area | Status |
|---|---|
| Backend | Spring Boot skeleton + Postgres/JPA/Flyway/Docker Compose infrastructure only. No domain entities of any kind exist. The old Step 3–7 roadmap is retired — see `backend/README.md`. |
| Frontend | Real, working PDF-reader/auth/chat thin client (Selection Engine, Room persistence, auth screens, chat UI) — predates the Card/Course Space pivot, not yet rebuilt around it. See `frontend/README.md`. |
| API contract | Retired — see `API_CONTRACT.md`. Old DTO shapes preserved there for historical reference only. |
| Architecture decisions | Retired — see `docs/DECISIONS.md`. |
| UX prototype | Exists as a standalone HTML/CSS/JS artifact (Card creation, resource add with processing states, Course Space creation/sharing/join simulation, Sarah with both workspace and contextual entry points). **Not committed to this repository** — it's a disposable testing tool, not a codebase to extend. |

## What's next (per the product owner's direction, not yet executed)

1. This cleanup (retiring old architecture references) — done as of this
   commit.
2. Build a new architecture/document hierarchy specifically for the
   prototype-first direction and for Antigravity as an implementation tool.
3. Derive the new domain model, API contract, and backend plan from the UX
   prototype plus the feature set below — not yet done.

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
