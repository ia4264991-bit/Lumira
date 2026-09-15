# Lumira — Implementation State

**Machine/agent-readable progress tracker.** Read this file, then verify it
against actual git/code state before trusting it — per
`docs/IMPLEMENTATION_PLAN.md`'s recovery rule, never assume this file is
correct without checking.

---

```
CURRENT_PHASE: Not started (architecture and planning complete, zero implementation milestones begun)
CURRENT_MILESTONE: None
STATUS: BLOCKED_ON_NOTHING — ready to begin B0
LAST_COMPLETED_MILESTONE: None
IN_PROGRESS: None
NEXT_MILESTONE: B0 — Infrastructure/Foundation
BLOCKERS: None currently known
LAST_VALIDATED_COMMIT: b6e2ec4 (architecture), <this commit> (planning docs)
LAST_VALIDATION: Repository inspected directly 2026-09-12 — confirmed zero domain entities exist beyond Step 1-2 infrastructure; confirmed frontend is real/working but predates the Card/Course Space pivot; confirmed API_CONTRACT.md is retired/historical only.
NOTES: Architecture (AD-001 through AD-056) is fully frozen and consistent as of commit b6e2ec4. This does NOT mean any implementation exists. Do not mark a milestone complete because its corresponding architecture decisions exist — architecture completion and implementation completion are different facts. Verify against actual code before updating this file.
```

---

## How to update this file

After completing a milestone (per `IMPLEMENTATION_PLAN.md`'s completion
rule — implementation + tests + validation against `DECISIONS.md` +
this-file-update + clean commit + no unresolved blocker):

1. Move it from its checklist `[ ]` to `[x]`, with the commit SHA that
   completed it.
2. Update `LAST_COMPLETED_MILESTONE`, `NEXT_MILESTONE`, `IN_PROGRESS`.
3. Update `LAST_VALIDATED_COMMIT` and `LAST_VALIDATION` with what was
   actually checked, not just "looks done."
4. If a blocker was hit, record it in `BLOCKERS` with enough detail that a
   fresh session understands it without chat history — cite the exact AD
   gap or contradiction, per `IMPLEMENTATION_PLAN.md`'s critical rule
   (stop and report, don't invent).

**Never mark something complete on the strength of "the architecture for
it exists."**

---

## Milestone checklist

### Backend

- [ ] B0 — Infrastructure/Foundation
- [ ] B1 — Identity + Personal Cards
- [ ] B2 — Course Spaces
- [ ] B3 — Resources
- [ ] B4 — Sharing + Authorization
- [ ] B5 — Notes + Study Sets
- [ ] B6 — Quizzes
- [ ] B7 — Flashcards
- [ ] B8 — Events + Notifications
- [ ] B9 — Sarah Foundation
- [ ] B10 — Sarah Generation
- [ ] B11 — Backend Integration + Security

### Android

- [ ] A0 — Android Foundation
- [ ] A1 — Authentication + Session
- [ ] A2 — Personal Cards/Home
- [ ] A3 — Course Spaces
- [ ] A4 — Resources/PDF
- [ ] A5 — Notes
- [ ] A6 — Study Sets
- [ ] A7 — Quizzes + Attempts
- [ ] A8 — Flashcards + Progress
- [ ] A9 — Sarah
- [ ] A10 — Activity + Notifications
- [ ] A11 — Full Android/Backend Integration + Hardening

**Note on Android's actual starting point:** unlike backend, Android is
*not* starting from zero — a real, working PDF-reader/auth/chat app exists
(Selection Engine complete, Room persistence, auth screens). None of that
existing code satisfies A0–A11 as scoped above (it predates Card/Course
Space entirely), but it is not nothing either — A0 explicitly begins by
inventorying what already works before changing it, not by rebuilding from
a blank project.

### Admin Web

- [ ] ADM-0 — Admin Web Foundation
- [ ] ADM-1 — Platform Overview
- [ ] ADM-2 — User Management
- [ ] ADM-3 — Content Moderation
- [ ] ADM-4 — Course Space Administration
- [ ] ADM-5 — AI Operations
- [ ] ADM-6 — Operational Tooling

**Note:** no admin web application exists in any form yet — this entire
track starts from nothing.

### Integration + Release

- [ ] I1 — Vertical Slice Integration
- [ ] I2 — End-to-End Workflows
- [ ] I3 — Security Validation
- [ ] I4 — MVP Hardening
- [ ] I5 — Production

---

## Verified-actual repository state (as of this file's creation)

Recorded here so a fresh session doesn't have to re-derive it from scratch,
but should still spot-check it rather than trust it blindly:

- Backend: Spring Boot skeleton, PostgreSQL/JPA/Flyway/Docker Compose,
  one migration (`V1__enable_pgcrypto_extension.sql`), one endpoint
  (`GET /v1/health`). Eleven package directories, all empty except a
  retired-scope `package-info.java`. Zero domain entities.
- Frontend: real, working Kotlin/Compose app — PDF reader, Selection
  Engine (complete), Room persistence, auth screens, chat UI. Predates the
  Card/Course Space pivot. `DebugAuthBypass` still in place (correctly —
  no real backend auth exists yet to replace it with).
- Admin web: does not exist.
- `API_CONTRACT.md`: retired, historical DTOs only, not a build target.
