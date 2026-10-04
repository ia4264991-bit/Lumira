# Lumira — Implementation State

**Machine/agent-readable progress tracker.** Read this file, then verify it
against actual git/code state before trusting it — per
`docs/IMPLEMENTATION_PLAN.md`'s recovery rule, never assume this file is
correct without checking.

---

```
CURRENT_PHASE: Android Client Implementation
CURRENT_MILESTONE: A0-A10 Android feature implementation
STATUS: IN_PROGRESS
LAST_COMPLETED_MILESTONE: B11 - Backend Integration + Security
IN_PROGRESS: Firebase email/password client auth, Firebase ID-token request interceptor, backend-backed Cards/Course Spaces home and workspace, Resource PDF upload/open, Notes, Study Sets, Quizzes, Flashcards, Sarah, Updates, and in-app notifications
NEXT_MILESTONE: Add Firebase-generated google-services.json, then build/debug in Android Studio; close gaps surfaced there and continue Course Space lifecycle management
BLOCKERS: Android has not been built or debugged yet. Firebase UID provisioning/onboarding remains deferred by AD-082, so valid Firebase users without a persisted mapping cannot access protected APIs. Firebase Android app configuration (google-services.json) must be added locally. Account deletion must preserve AD-082's cross-system Firebase identity deletion requirement; backend support is not yet confirmed. Summary generation remains deferred under AD-081.
LAST_VALIDATED_COMMIT: c0287871c1593a559acc618088d8efda16742ae8
IMPLEMENTATION_COMMITS: B2 architecture clarification `7cddade`; ownership transfer `91eae6e`; transfer integration tests `82ecd3c`; B2 in-progress state `03ce622`; integrity-trigger migration and lifecycle assertion fix `b08dbb5`; B4 sharing and authorization `561c422fa294e792221fabe690e83498f3ea04b6`; B6 Quizzes `931dee50f4ee8c08114e09fdba27664dcf5e3409`; B7 Flashcards `ceb0bb5518866dd2595be5bef17083f11c4ceec7`; B8 Events + Notifications `1076477461e469f22c5dff68187e6d59d0201c7`.
LAST_VALIDATION: 2026-10-04 - User-run full backend Maven suite passed: 101 tests, 0 failures, 0 errors, 0 skipped, including Firebase UID uniqueness integration coverage. The focused standalone MockMvc suite also passed 10/10 after explicitly selecting Jackson converters. Prior backend baseline at c0287871c1593a559acc618088d8efda16742ae8 passed 91/91 tests on 2026-10-03. Android implementation added in this session is unbuilt/unverified; user will build and debug it in Android Studio.
ARCHITECTURE_BLOCKER: No conflict in AD-082 for verification/mapping. User provisioning remains explicitly deferred; unknown Firebase UIDs fail closed. Summary generation remains deferred under AD-081 because its persistence/content/API contract is undefined.
NOTES: B2 is complete; its validation source is `b08dbb5`, and closure documentation is committed at `2f6a2c1`. B3 Resources is implemented, PostgreSQL-validated, committed, and pushed at `680e7ebfd3ef4cdac39dc05dcc3fdf41c72ffdf1`; the user-run suite reported 42 tests with no failures or errors. Direct invitations use INVITED membership episodes per AD-070; do not introduce PENDING as a membership status. The V5 migration fixes the row-type access defect in the deferred V4 owner/member-Card integrity trigger. B3 introduces no ResourceVersion/ContentVersion or asynchronous processing infrastructure. B4 sharing and authorization were implemented, PostgreSQL-validated with 55 passing tests, and committed at `561c422fa294e792221fabe690e83498f3ea04b6`; B4 closure documentation is at `91afb90a7d48816ee329b5b3809d2ce9c0793cdd`. B5 Notes + Study Sets are implemented under AD-076, PostgreSQL-validated with 60 passing tests, and committed at `4806efdff861e7a8eb009d58edb02abb3f413d73`; closure documentation is at `72d5c9d34339d617fae6d52f3f7eb02389a9a66a`. B6 Quizzes is complete under AD-077 at `931dee50f4ee8c08114e09fdba27664dcf5e3409`, validated by the 64-test PostgreSQL suite. AD-078 freezes the B7 MVP model in commit `1a62d523b9f74471cba9a7d7767cb3e408efdc43`. B7 Flashcards was validated against PostgreSQL (70/70 tests) and committed as `ceb0bb5518866dd2595be5bef17083f11c4ceec7`. AD-079 freezes B8's notification recipient references and event-specific policies. B8 was validated against PostgreSQL (79/79 tests) and committed as `1076477461e469f22c5dff68187e6d59d0201c7b`. AD-080 resolves B9's transient `conversationId` and untrusted `conversationHistory` semantics. Current B9 changes include transient request/response APIs, per-request authorized context assembly, a provider-neutral AI Router, atomic UTC-month request metering, V12 usage storage, and PostgreSQL integration tests. B9 is committed and PostgreSQL-validated at `857630e39fdb66dea90c92bb2d7f0a5780c45615` (84/84). B10 Sarah Generation is committed at `3e7adaa5ad21be9db9f63a60d337073cd2674145` and passed the full 88-test PostgreSQL suite. It implements generation for FlashcardSet, Quiz, and StudySet; Summary generation remains deferred under AD-081. B11 Backend Integration + Security is committed at `c0287871c1593a559acc618088d8efda16742ae8` and passed the full 91-test PostgreSQL suite.
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
- [x] B5 — Notes + Study Sets (`4806efdff861e7a8eb009d58edb02abb3f413d73`; 60/60 PostgreSQL integration tests)
- [x] B6 — Quizzes (`931dee50f4ee8c08114e09fdba27664dcf5e3409`; 64/64 PostgreSQL integration tests)
- [x] B7 — Flashcards (`ceb0bb5518866dd2595be5bef17083f11c4ceec7`; 70/70 PostgreSQL integration tests)
- [x] B8 — Events + Notifications (`1076477461e469f22c5dff68187e6d59d0201c7b`; 79/79 PostgreSQL integration tests)
- [x] B9 — Sarah Foundation (`857630e39fdb66dea90c92bb2d7f0a5780c45615`; 84/84 PostgreSQL tests)
- [x] B10 — Sarah Generation (`3e7adaa5ad21be9db9f63a60d337073cd2674145`; 88/88 PostgreSQL tests; Summary deferred under AD-081)
- [x] B11 — Backend Integration + Security (`c0287871c1593a559acc618088d8efda16742ae8`; 91/91 PostgreSQL tests)

### Android

- [ ] A0 — Android Foundation (Card-first shell and Card workspace; Android Studio validation pending)
- [ ] A1 — Authentication + Session (Firebase client wired; google-services.json and Android Studio validation pending)
- [ ] A2 — Personal Cards/Home (backend list/create and Course Space join wired; validation pending)
- [ ] A3 — Course Spaces (sharing, invite links, join, membership, roles, join requests wired; direct invitation inbox/lifecycle still to add)
- [ ] A4 — Resources/PDF (PDF upload/download/read wired; non-PDF resource flows and cross-account validation pending)
- [ ] A5 — Notes (create/read/edit/delete wired; validation pending)
- [ ] A6 — Study Sets (create/read/edit/delete wired; validation pending)
- [ ] A7 — Quizzes + Attempts (create/take/attempt history wired; full quiz editing and validation pending)
- [ ] A8 — Flashcards + Progress (create/study/review/delete wired; progress history and validation pending)
- [ ] A9 — Sarah (workspace/contextual ask and generation wired; validation pending)
- [ ] A10 — Activity + Notifications (Updates and in-app notifications wired; validation pending)
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
