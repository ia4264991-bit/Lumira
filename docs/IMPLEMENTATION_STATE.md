# Lumira — Implementation State

**Machine/agent-readable progress tracker.** Read this file, then verify it
against actual git/code state before trusting it — per
`docs/IMPLEMENTATION_PLAN.md`'s recovery rule, never assume this file is
correct without checking.

---

```
CURRENT_PHASE: Backend Implementation
CURRENT_MILESTONE: B1 — Identity + Personal Cards
STATUS: CODE_COMPLETE_TESTS_WRITTEN — awaiting test execution against live PostgreSQL in build environment
LAST_COMPLETED_MILESTONE: B0 — Infrastructure / Foundation
IN_PROGRESS: B1 — Identity + Personal Cards
NEXT_MILESTONE: B2 — Course Spaces
BLOCKERS: None currently known. Java/Maven not available in current agent environment — tests must be run externally before B1 can be marked complete.
LAST_VALIDATED_COMMIT: <this commit>
LAST_VALIDATION: 2026-09-30 — B1 code written: V2__user_and_card.sql migration, User entity+repo, Card entity+repo+service+controller, CardIntegrationTest. Tests cover AD-048 (multiple cards per user), AD-056 (cross-user 403), auth boundary (401 on no token), blank-name 400. Not yet executed against live PostgreSQL — must be verified before B1 exit criteria are met.
NOTES: B1 implementation is structurally complete. To finalize B1: run `mvn test` against real PostgreSQL, confirm all tests pass, then update this file to COMPLETED with the commit SHA and move to B2.
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

- [x] B0 — Infrastructure/Foundation
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

## Verified-actual repository state (as of B0 completion)

- Backend: Spring Boot 3.3.4, PostgreSQL/JPA/Flyway, baseline migration (`V1__enable_pgcrypto_extension.sql`), `/v1/health` endpoint. Error handling envelope (`ApiErrorResponse`, `GlobalExceptionHandler`, `LumiraException`), AD-057 polymorphic ownership mapping (`ArtifactOwner`, `BaseEntity`), security scaffolding (`@CurrentUser`, `SecurityInterceptor`, `TokenResolver`), and real PostgreSQL integration test infrastructure (`BaseIntegrationTest`, `DatabaseIntegrationTest`). All 19 tests passing.
- Frontend: real, working Kotlin/Compose app — PDF reader, Selection Engine (complete), Room persistence, auth screens, chat UI. Predates the Card/Course Space pivot. `DebugAuthBypass` still in place (correctly — awaiting B1).
- Admin web: does not exist.
- `API_CONTRACT.md`: Live, authoritative.
