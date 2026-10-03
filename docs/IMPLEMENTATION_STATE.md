# Lumira — Implementation State

**Machine/agent-readable progress tracker.** Read this file, then verify it
against actual git/code state before trusting it — per
`docs/IMPLEMENTATION_PLAN.md`'s recovery rule, never assume this file is
correct without checking.

---

```
CURRENT_PHASE: Backend Implementation
CURRENT_MILESTONE: B5 - Notes + Study Sets
STATUS: IN_PROGRESS
LAST_COMPLETED_MILESTONE: B4 - Sharing + Authorization
IN_PROGRESS: B5 - Notes + Study Sets
NEXT_MILESTONE: B6 - Quizzes
BLOCKERS: None known for B0-B4.
LAST_VALIDATED_COMMIT: 561c422fa294e792221fabe690e83498f3ea04b6
IMPLEMENTATION_COMMITS: B2 architecture clarification `7cddade`; ownership transfer `91eae6e`; transfer integration tests `82ecd3c`; B2 in-progress state `03ce622`; integrity-trigger migration and lifecycle assertion fix `b08dbb5`; B4 sharing and authorization `561c422fa294e792221fabe690e83498f3ea04b6`.
LAST_VALIDATION: 2026-10-03 - User-run `mvn clean test -DforkCount=1 -DreuseForks=false` from the backend directory completed with BUILD SUCCESS against PostgreSQL: 55 tests run, 0 failures, 0 errors, 0 skipped. Validated commit: `561c422fa294e792221fabe690e83498f3ea04b6`.
ARCHITECTURE_BLOCKER: None for B2 or B3. AD-071 (2026-10-03) additively resolves the AD-066/AD-067 transfer/memberCardId conflict. AD-072 clarifies the physical owner column names without changing AD-057 semantics.
NOTES: B2 is complete; its validation source is `b08dbb5`, and closure documentation is committed at `2f6a2c1`. B3 Resources is implemented, PostgreSQL-validated, committed, and pushed at `680e7ebfd3ef4cdac39dc05dcc3fdf41c72ffdf1`; the user-run suite reported 42 tests with no failures or errors. Direct invitations use INVITED membership episodes per AD-070; do not introduce PENDING as a membership status. The V5 migration fixes the row-type access defect in the deferred V4 owner/member-Card integrity trigger. B3 introduces no ResourceVersion/ContentVersion or asynchronous processing infrastructure. B4 sharing and authorization were implemented, PostgreSQL-validated with 55 passing tests, and committed at `561c422fa294e792221fabe690e83498f3ea04b6`. B5 is the active milestone.
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
- [x] B1 — Identity + Personal Cards
- [x] B2 — Course Spaces (`b08dbb53a634a626d4606ad244af7041d778e911`; 33/33 PostgreSQL integration tests)
- [x] B3 — Resources (`680e7ebfd3ef4cdac39dc05dcc3fdf41c72ffdf1`; 42/42 PostgreSQL integration tests)
- [x] B4 — Sharing + Authorization (`561c422fa294e792221fabe690e83498f3ea04b6`; 55/55 PostgreSQL integration tests)
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
