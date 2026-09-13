# Lumira

> ⚠️ **OLD ARCHITECTURE RETIRED — 2026-09-12.** This repository previously
> implemented an academic-hierarchy model (University → Semester → Course →
> CourseOffering, per an internal "LPTS" specification). That architecture
> has been formally retired. It is no longer authoritative and must not be
> continued.

## New target: Lumira Prototype / MVP

Lumira is being rebuilt around a validated UX prototype (Card-first study
tool, with Course Spaces as the sharing/collaboration layer). The prototype
is the current product/UX reference. A new architecture is being built
around it — **that architecture has not been written yet.** This repository
is in a deliberate clean-slate state between the old and new architecture;
do not infer a domain model from what remains here.

Planned scope for the new direction (not yet architected — see
`docs/LUMIRA_STATE.md`):

- **Card** — the central user study workspace
- Resources, Notes, Study Sets, Quizzes, Flashcards
- **Sarah** — first-class AI tutor/workspace capability, not a bolt-on
- **Course Spaces** — Owner/Admin/Member roles, sharing, shared resources
  and explicitly shared study artifacts
- Notifications, offline/download capability where appropriate
- Network-effect/growth mechanics
- Authentication/account foundation
- A functional backend supporting the prototype

## Repository layout

```
Lumira/
├── backend/    Spring Boot skeleton + Postgres/JPA/Flyway/Docker Compose
│               infrastructure. No domain entities currently exist —
│               the ones that used to be planned here belonged to the
│               retired architecture. See backend/README.md.
└── frontend/   Android app (Kotlin, Jetpack Compose) — currently still
                shaped like a PDF-reader/auth/chat thin client, predating
                the Card/Course Space pivot. Real, working code; not yet
                rebuilt around the new product direction. See
                frontend/README.md.
```

## Document hierarchy

The old document hierarchy (LPTS → `docs/DECISIONS.md` → `docs/LUMIRA_STATE.md`
→ `API_CONTRACT.md` → `docs/CLAUDE_PROJECT_RULES.md`) governed the retired
architecture. **A new hierarchy for the prototype-first direction has not
been created yet** — this is intentional; building it is the next task
after this cleanup, not part of it. Each of those files now carries its own
retirement notice rather than silently continuing to claim authority it no
longer has.

## Working process

Backend and frontend continue to be developed in separate
conversations/sessions, sharing this repo so each side can inspect the
other's current state. Pull the latest `master` before starting work.
