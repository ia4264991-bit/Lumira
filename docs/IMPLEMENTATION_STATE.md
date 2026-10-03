# Lumira — Implementation State

**Machine/agent-readable progress tracker.** Read this file, then verify it
against actual git/code state before trusting it — per
`docs/IMPLEMENTATION_PLAN.md`'s recovery rule, never assume this file is
correct without checking.

---

```
CURRENT_PHASE: Backend Implementation
CURRENT_MILESTONE: B2 - Course Spaces
STATUS: IN_PROGRESS
LAST_COMPLETED_MILESTONE: B1 - Identity + Personal Cards
IN_PROGRESS: B2 ownership transfer, PostgreSQL integration coverage, and full-suite verification
NEXT_MILESTONE: Complete B2 - Course Spaces
BLOCKERS: Full Maven validation is blocked in this Windows sandbox: `javac` throws `java.nio.file.AccessDeniedException` in `ZipFileSystemProvider.removeFileSystem` while closing a dependency JAR (`micrometer-commons-1.13.4.jar` / `jackson-dataformat-toml-2.17.2.jar`). Reproduced with Java 21, 24, and 25, with Maven caches in `%USERPROFILE%\.m2`, `%TEMP%`, and a writable workspace folder, and with `-DforkCount=1 -DreuseForks=false`; no tests ran. No leftover Maven/Java process was found. Sysinternals Handle was not installed and its download failed with a PowerShell TLS connection-close error; Resource Monitor inspection was unavailable because the Windows UI helper failed to write kernel assets. The project is outside OneDrive. Defender preference query returned Access Denied; automatic approval review rejected adding exclusions because it weakens system security. Docker is absent and no WSL distribution is installed. PostgreSQL 18.3 service is running and accepting connections on localhost:5432.
LAST_VALIDATED_COMMIT: fa4134e86fa94ffa6056ae147c32aa339435bb4b (B1 validation commit; see LAST_VALIDATION)
IMPLEMENTATION_COMMITS: AD-071 docs `7cddade`; ownership transfer `91eae6e`; transfer integration tests `82ecd3c` (local, not yet pushed; unvalidated)
LAST_VALIDATION: 2026-10-03 - `mvn clean test -DforkCount=1 -DreuseForks=false` failed during main-source compilation while javac closed a dependency JAR; Surefire/test phase was not reached (0 tests executed). Reproduced with JDK 21/24/25 and alternate local Maven cache paths. Prior B1 validation: user-provided external Maven output on 2026-10-02 reports BUILD SUCCESS, 27 tests passing on PostgreSQL 18.3, with three Flyway migrations validated and V3 applied; this does not validate B2 or current HEAD.
ARCHITECTURE_BLOCKER: None. AD-071 (2026-10-03) additively resolves the AD-066/AD-067 transfer/memberCardId conflict; implementation and real PostgreSQL validation remain outstanding.
NOTES: Direct invitations use INVITED membership episodes per AD-070; do not introduce PENDING as a membership status. Transfer implementation and PostgreSQL integration tests are committed locally but unverified. Preserve the deferred V4 owner/member-Card trigger. B2 remains IN_PROGRESS until its full real PostgreSQL suite passes.
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
