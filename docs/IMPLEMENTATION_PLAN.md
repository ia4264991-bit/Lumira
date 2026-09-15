# Lumira — Implementation Plan

**This document is a roadmap, not an architecture authority.** It translates
already-frozen decisions into an ordered build sequence. It never overrides
`docs/DECISIONS.md` or `docs/LUMIRA_STATE.md` — see the governance section
below for what happens when an implementation question isn't already
decided there.

Companion file: `docs/IMPLEMENTATION_STATE.md` tracks actual progress and
lets a fresh session resume without chat history.

---

## 1. Governance

### Source-of-truth hierarchy (unchanged, restated for this document's scope)

`docs/DECISIONS.md` (frozen ADs) → `docs/LUMIRA_STATE.md` (current status) →
`API_CONTRACT.md` (network boundary, currently retired/historical — see
its own banner) → `docs/CLAUDE_PROJECT_RULES.md` (process rules) → this
file (execution order only).

**This plan does not decide architecture.** Every milestone below is
scoped to what AD-001–AD-056 already permit. If a milestone turns out to
need a decision that isn't in `DECISIONS.md`, that is not this document's
job to invent.

### Critical implementation rule

If an implementation agent (Antigravity, Claude Code, Android Studio/
Gemini, or any future agent) encounters an architectural question that is
not already decided in `docs/DECISIONS.md`:

**STOP. Do not invent a new architecture decision. Report the conflict to
the Architecture/Integration governance role.**

This is not optional and does not get relaxed under schedule pressure.

### Agent responsibilities

| Role | Responsibility |
|---|---|
| **Architecture/governance** | Reviews architecture, resolves or escalates architectural conflicts, audits implementation against `DECISIONS.md`. Does not implement. |
| **Backend implementation** | Implements backend milestones (B0–B11) only. |
| **Android implementation** | Implements Android milestones (A0–A11) only. |
| **Admin web implementation** | Implements admin milestones (ADM-0–ADM-6) only. |
| **Integration/review** | Validates cross-system behavior (I1–I5). |

This plan is tool-independent. Antigravity, Claude Code, and Android
Studio/Gemini are mentioned only as *current* operational choices for
these roles, not as architecture.

### Milestone completion rule

A milestone is complete only when **all** of the following hold:
1. Implementation exists.
2. Tests appropriate to the milestone pass.
3. Behavior is validated against the authoritative architecture (not just
   "it compiles").
4. `docs/IMPLEMENTATION_STATE.md` is updated.
5. A clean commit exists.
6. No known unresolved blocker remains within that milestone's scope.

**Architecture completion is not implementation completion.** All 56 ADs
being frozen means the *plan* is buildable — it means nothing about how
much of it has actually been built.

### Recovery rule (for a fresh session after interruption/quota loss)

1. Read the repository (don't trust chat history).
2. Read `docs/DECISIONS.md` and `docs/LUMIRA_STATE.md`.
3. Read `docs/IMPLEMENTATION_STATE.md`.
4. Inspect actual code/git state — confirm the state file matches reality.
5. Identify the last genuinely completed milestone.
6. Continue from the next unfinished task.
7. **Never redo completed work without concrete evidence it's invalid.**

---

## 2. Current repository state (verified, not assumed)

As of commit `b6e2ec4`:

- **Backend**: Spring Boot skeleton + PostgreSQL/JPA/Flyway/Docker Compose
  infrastructure only. One migration (`V1__enable_pgcrypto_extension.sql`).
  One real endpoint (`GET /v1/health`). Eleven package directories, all
  containing only a retired/neutral `package-info.java` — **zero domain
  entities of any kind exist.**
- **Frontend**: real, working PDF-reader/auth/chat thin client (Selection
  Engine complete, Room persistence, auth screens, chat UI) — predates the
  Card/Course Space pivot, not yet rebuilt around it.
- **`API_CONTRACT.md`**: retired. Historical DTO shapes preserved for
  reference only, not a build target.
- **No admin web application exists yet in any form.**

**Every milestone below starts from this baseline, not from an assumed
head start.**

---

## 3. MVP boundary

Lumira MVP = the core academic workflow, end to end, integrated and
security-tested:

`Identity → Card → Resources → Notes/Study Sets → Quizzes → Flashcards →
Course Spaces/sharing → Sarah → Activity/notifications → Admin operations
→ integrated/security-tested system.`

**Explicitly deferred, per existing repository decisions — not part of
MVP, do not silently pull forward:**
- Offline/download capability (`DECISIONS.md`'s standing extension point)
- Video Overview (`LUMIRA_STATE.md`'s planned-features list — cost/
  bandwidth deferred)
- "Buy Course" / "Community" beyond a Coming-Soon placeholder
- Any subscription/payment infrastructure — no canonical decision requires
  it; do not introduce it as part of this plan
- Generalized `ContentVersion`, `SarahSession` schema, exact token/session
  architecture, sequence-numbered event ordering, direct-object-storage
  upload mechanics — all explicitly deferred per the red-team reconciliation
  (`DECISIONS.md` Revisions log, 2026-09-12)

---

## 4. Backend milestones

### B0 — Infrastructure/Foundation
- **Objective**: Confirm and extend the existing Spring Boot foundation to a state ready for real domain work.
- **Scope**: Verify current skeleton (already done — see §2); establish repository/service/controller conventions; testing foundation (integration test pattern against real Postgres, per `CLAUDE_PROJECT_RULES.md` Rule 7's honesty-about-verification requirement); basic error-handling/response-shape convention; foundational security infrastructure scaffolding (not full auth — see B1).
- **Dependencies**: None — builds on existing Steps 1–2 infra.
- **Deliverables**: Documented conventions (package layout, error response shape, test pattern); no new domain code.
- **Tests/validation**: `mvn test` passes against real Postgres; health check still works.
- **Exit criteria**: A second engineer/agent could add a new module and know exactly where each layer goes.

### B1 — Identity + Personal Cards
- **Objective**: Real `User` and `Card` entities, matching AD-019/020/029/048.
- **Scope**: Internal user identity model (stable `userId`, decoupled from any future auth-provider identity); authentication/session boundary only as far as current architecture defines it (full token/session architecture is explicitly deferred per the red-team reconciliation — build the minimum boundary needed to attribute a Card to a user, not a complete auth system); Card entity and lifecycle (create, view); **AD-048: a user may own zero or more Cards — no one-primary-Card constraint, ever.**
- **Dependencies**: B0.
- **Deliverables**: `User`, `Card` entities + migrations; repositories; minimal auth boundary sufficient to attribute requests to a user; `POST/GET /v1/cards` per whatever contract is derived from `DECISIONS.md` (not the retired `API_CONTRACT.md`).
- **Tests/validation**: A user can own multiple Cards; Card ownership is enforced; no cross-user Card access.
- **Exit criteria**: Multiple named Cards, per user, persisted and retrievable — matching the validated UX prototype's repeatable Create-Card flow.

### B2 — Course Spaces
- **Objective**: Course Space behavior as a Card capability, per AD-019 (no separate entity).
- **Scope**: Sharing-enabled flag on Card; `card_membership` (AD-023: `id, cardId, userId, role, joinedAt`); membership status lifecycle (AD-032: `INVITED/ACTIVE/LEFT/REMOVED`); roles (Owner/Admin/Member, AD-023/034); ownership transfer (AD-042/043); leave/remove (AD-031/032); rejoin reuses existing Card (AD-033).
- **Dependencies**: B1.
- **Deliverables**: Migration adding sharing/membership tables; membership lifecycle service; ownership-transfer operation (single atomic action per AD-043); join-link generation/reset (AD-024).
- **Tests/validation**: Owner cannot leave without transfer (AD-042); no ownerless Course Space ever; rejoin does not fork a new Card (AD-033); Admin cannot promote/demote (AD-034).
- **Exit criteria**: A Card can become a Course Space, accept members with roles, and support ownership transfer — all without a `CourseSpace` table existing anywhere (AD-019).

### B3 — Resources
- **Objective**: Resource/ResourceVersion per the live architecture's Resource model.
- **Scope**: Resource entity, ownership (Card or User, per the AD-021 pattern re-derived for the new model); storage integration (local disk — direct object storage is explicitly deferred, per §3); access control tied to Card/Course Space ownership.
- **Dependencies**: B1, B2 (for Course-Space-scoped resources).
- **Deliverables**: `Resource` entity + migration; upload endpoint (synchronous/local — async ingestion deferred); ownership enforcement.
- **Tests/validation**: A private Resource is inaccessible to non-owners; a Course-Space Resource is accessible only to authorized members.
- **Exit criteria**: A user can add a Resource to their own Card.

### B4 — Sharing + Authorization
- **Objective**: The explicit share-record mechanism (AD-045) and the authorization boundary (AD-041/056).
- **Scope**: Share record linking artifact→Course Space (not a same-row boolean, per AD-045); unshare/force-unshare authority matrix (AD-044); moderation reasons (AD-047); event generation for share/unshare actions (AD-026 extended types); **deterministic backend authorization for every protected operation — never inferred from object existence, client claims, or cached state (AD-056).**
- **Dependencies**: B1, B2, B3.
- **Deliverables**: Share-record entity; unshare/force-unshare endpoints; authorization check applied uniformly (not scattered per-endpoint logic).
- **Tests/validation**: BOLA/IDOR-style probes — an object's existence must never imply access; force-unshare never deletes the artifact or changes ownership (AD-044); sharing survives the sharer leaving Course Space (AD-040) *and* survives account deletion via reassignment to the Course Space's own Card, not a placeholder (AD-050).
- **Exit criteria**: Every sharing/authorization invariant in AD-040/041/044/045/046/047/056 has a passing test, not just a code path.

### B5 — Notes + Study Sets
- **Objective**: Two more first-class Card artifacts (AD-029).
- **Scope**: `Note`, `StudySet` entities; ownership/sharing exactly per the AD-030 no-forking pattern (reuse the B4 share mechanism, don't build a parallel one).
- **Dependencies**: B1, B4.
- **Deliverables**: Two entities + migrations; CRUD endpoints; share/unshare wired through the existing B4 mechanism.
- **Tests/validation**: No `PersonalNote`/`CourseSpaceNote` type-forking exists anywhere in the schema (AD-030).
- **Exit criteria**: Notes and Study Sets behave identically to Resources with respect to ownership/sharing, using the same mechanism, not a copy of it.

### B6 — Quizzes
- **Objective**: Quiz artifact + the canonical-vs-personal-state boundary (AD-053).
- **Scope**: `Quiz` entity (canonical structure/content); **`QuizAttempt` as a separate per-user execution-state entity** — one user's attempt/answers/score against a specific Quiz, never mutating the Quiz itself; sharing via the B4 mechanism.
- **Dependencies**: B1, B4, B5 (reuses the same sharing pattern).
- **Deliverables**: `Quiz` + `QuizAttempt` entities (exact schema for attempt-to-content-version binding may remain minimal — AD-053 explicitly defers this detail); endpoints for creating a Quiz and recording an attempt.
- **Tests/validation**: Two different users' attempts against the same shared Quiz never collide or leak into each other; editing the Quiz doesn't retroactively corrupt past attempts' meaning.
- **Exit criteria**: A shared Quiz can be attempted independently by multiple Course Space members without their attempts becoming shared or visible to each other.

### B7 — Flashcards
- **Objective**: Flashcard Set artifact + personal progress boundary (AD-053).
- **Scope**: `FlashcardSet` entity (canonical content); per-user flashcard progress/review-state entity, same boundary principle as B6's `QuizAttempt`.
- **Dependencies**: B1, B4, B6 (mirrors the same pattern — build B6 first to establish it once).
- **Deliverables**: `FlashcardSet` + progress-state entities; endpoints.
- **Tests/validation**: Same as B6 — personal progress never mutates or leaks across users.
- **Exit criteria**: Same shape as B6's exit criteria, for Flashcards.

### B8 — Events + Notifications
- **Objective**: The single event-log model (AD-026) powering both the activity feed and notifications.
- **Scope**: `CourseSpaceEvent` (open string `type`, seeded per AD-026 plus all additive extensions: `MEMBER_PROMOTED/DEMOTED`, `OWNERSHIP_TRANSFERRED`, `CONTENT_UNSHARED/FORCE_UNSHARED`, `COURSE_SPACE_DISSOLVED`); `notification` as a delivery/read-state wrapper around an event (AD-026) — never a freestanding fact; transactional consistency between the triggering action and its event (an event must never be recorded for an action that didn't actually happen, or vice versa).
- **Dependencies**: B2, B4 (most event types are emitted by Course Space/sharing actions already built there).
- **Deliverables**: Event log table; notification table; a single emission path used by every action that needs to notify (not a separate ad hoc notification call site per feature).
- **Tests/validation**: Every notification traces back to a real event; no code path creates a notification without one.
- **Exit criteria**: The Updates feed and per-member notifications are both provably derived from the same underlying log, not two systems that happen to agree today.

### B9 — Sarah Foundation
- **Objective**: The authorization-first AI request boundary (AD-028/037/054/056).
- **Scope**: Sarah request endpoint boundary (both entry-point shapes from AD-027 — workspace and contextual); **authorization determined and enforced before any content enters retrieval, never after (AD-054)**; authorized context builder (respects AD-028's boundary — Course Space Sarah may use shared content + the requester's own private content, never another member's private content); AI Router as an abstraction layer (AD-038) — no model-provider credentials or selection logic reaching the Android client (AD-037); server-authoritative usage metering (AD-037) — the client never calculates or enforces its own limit.
- **Dependencies**: B1, B2, B4 (needs the full authorization boundary from B4 to actually enforce AD-054 correctly, not just claim to).
- **Deliverables**: `/v1/sarah/ask`-shaped endpoint (or equivalent) carrying a `cardId` for workspace entry, per `API_CONTRACT.md`'s already-flagged gap; context-builder service; AI Router interface (provider-agnostic); usage-tracking mechanism.
- **Tests/validation**: A Course Space member's Sarah request never retrieves another member's private content, even adversarially crafted requests; usage counter is unaffected by client-side manipulation.
- **Exit criteria**: Sarah can answer a question grounded in authorized context only — generation itself is not required yet (that's B10).

### B10 — Sarah Generation
- **Objective**: AI-generated artifacts as ordinary Lumira artifacts (AD-035/036/055).
- **Scope**: Summary, Flashcard, Quiz, and Study Set generation via Sarah — **in MVP scope, not deferred for being AI (AD-035)**; generated artifacts persisted as ordinary rows of their normal type, generation method as at most a provenance attribute, never a distinct type (AD-036, AD-030's no-forking rule applied); **Sarah's output validated — schema, domain, and authorization validation — before persistence, never trusted merely because it was generated (AD-055).**
- **Dependencies**: B5, B6, B7 (the artifact types must exist first), B9 (the authorized pipeline).
- **Deliverables**: Generation endpoints per artifact type, routed through B9's pipeline; output validation layer.
- **Tests/validation**: A malformed or adversarial Sarah output is rejected before it reaches persistence — this needs an actual adversarial test, not just a happy-path one; a generated Quiz is indistinguishable in the schema from a manually created one except for its provenance attribute.
- **Exit criteria**: Sarah can generate a real, stored, editable, shareable artifact of each of the four types.

### B11 — Backend Integration + Security
- **Objective**: Prove the invariants, not just the features.
- **Scope**: API integration tests across the full backend; explicit BOLA/IDOR test suite; cross-user access attempts (should all fail); membership lifecycle tests (join/leave/remove/rejoin); ownership-transfer tests; sharing/unsharing/force-unshare tests including the moderation-reason requirement; account-deletion tests against AD-049–052 specifically (private data purged, shared content survives via reassignment, Course-Space-Owner precondition enforced, event history retained); Sarah security tests (authorization-before-retrieval, output validation); basic performance sanity (N+1 queries, pagination behavior) — not full-scale load testing, which belongs later in I4.
- **Dependencies**: All of B1–B10.
- **Deliverables**: A test suite that specifically targets each numbered AD's invariant, not just general coverage.
- **Tests/validation**: Is the test suite itself.
- **Exit criteria**: Every AD referenced in this plan (§10 below) has at least one test that would fail if that AD were violated.

---

## 5. Android milestones

The Android client remains a thin client throughout (existing principle,
`frontend/README.md`, reaffirmed by AD-037): **it must never contain
provider credentials, provider-selection logic, authoritative authorization
decisions, or server-side business rules.** Every milestone below consumes
a backend API; none of them re-implement backend logic.

### A0 — Android Foundation
- **Objective**: Confirm the existing app shape and prepare it for the Card/Course Space rebuild.
- **Scope**: Verify current Selection Engine, Room, and navigation structure (real, working, per the earlier audit); do not redesign the Selection Engine (it's frozen/working); plan the navigation change from the old PDF-reader-first shape to Card-first.
- **Dependencies**: None (frontend work is independent of backend readiness for this milestone specifically).
- **Deliverables**: Updated navigation shell reflecting Card/Course Space as the primary structure; no backend calls yet.
- **Tests/validation**: Existing Selection Engine tests (if any) still pass, confirming nothing was broken.
- **Exit criteria**: App opens to a Card-first home, even if backed by local/mock state.

### A1 — Authentication + Session
- **Objective**: Replace `DebugAuthBypass` with the real minimal auth boundary from B1.
- **Scope**: Real login/session against B1's boundary; secure credential storage on-device (no plaintext long-lived credentials); remove `DebugAuthBypass` once real auth exists, not before.
- **Dependencies**: B1.
- **Deliverables**: Auth screens wired to the real backend; secure token storage.
- **Tests/validation**: No credentials logged or stored in plaintext.
- **Exit criteria**: A real user can log in and their session persists appropriately.

### A2 — Personal Cards/Home
- **Objective**: The Cards/Course Spaces two-tab home, per the validated UX prototype.
- **Scope**: Create Card flow (repeatable — AD-048); Cards list; Course Spaces filtered view (same underlying data, two views, per AD-019).
- **Dependencies**: B1, A1.
- **Deliverables**: Home screen matching the prototype's validated structure.
- **Tests/validation**: Creating multiple Cards works, matching AD-048 exactly.
- **Exit criteria**: Functionally equivalent to the validated UX prototype's Cards/Course Spaces tabs, against a real backend.

### A3 — Course Spaces
- **Objective**: Create/share/join/manage Course Spaces from Android.
- **Scope**: Create-Course-Space flow; share-link generation/reset UI; join flow (including the approval-required case); member list with role badges; ownership-transfer UI (minimal, single-action per AD-043); leave/remove UI.
- **Dependencies**: B2, A2.
- **Deliverables**: Full Course Space UI matching the prototype's validated flows.
- **Tests/validation**: Manual/instrumented test of the full create→share→join loop against a real backend.
- **Exit criteria**: Matches the UX prototype's Course Space behavior, for real.

### A4 — Resources/PDF
- **Objective**: Real resource upload/viewing wired to B3.
- **Scope**: Upload UI; existing PDF viewer/Selection Engine wired to real, backend-served Resources instead of local-only files.
- **Dependencies**: B3, A3.
- **Deliverables**: Working upload + view flow against the real backend.
- **Tests/validation**: A shared Resource is visible to authorized Course Space members on a different account.
- **Exit criteria**: The existing Selection Engine now operates on real backend-served content, not just local files.

### A5 — Notes
- **Objective**: Notes UI.
- **Scope**: Create/view/edit Notes; share toggle.
- **Dependencies**: B5, A2.
- **Deliverables**: Notes tab inside a Card.
- **Tests/validation**: Private-by-default, explicit share confirmed.
- **Exit criteria**: Matches prototype behavior against real backend.

### A6 — Study Sets
- **Objective**: Study Set UI.
- **Scope**: Same shape as A5, for Study Sets.
- **Dependencies**: B5, A2.
- **Deliverables/Tests/Exit criteria**: Same pattern as A5.

### A7 — Quizzes + Attempts
- **Objective**: Quiz-taking UI with the canonical-vs-attempt boundary visible to the user (e.g., "your attempt" vs. the shared quiz).
- **Scope**: View/take a Quiz; record an attempt; view past attempts (own only).
- **Dependencies**: B6, A2.
- **Deliverables**: Quiz UI including the customize/generate sheet already validated in the prototype (AI Usage meter included, reading a **backend-computed** value per AD-037/A9's dependency).
- **Tests/validation**: Two accounts attempting the same shared Quiz never see each other's attempts.
- **Exit criteria**: Matches prototype's Customize Quiz flow, against real attempts.

### A8 — Flashcards + Progress
- **Objective**: Mirrors A7 for Flashcards.
- **Scope/Dependencies/Deliverables/Tests/Exit criteria**: Same pattern as A7, for `FlashcardSet`/progress.

### A9 — Sarah
- **Objective**: Both Sarah entry points, wired to B9/B10.
- **Scope**: Persistent workspace-Sarah button (already prototyped); contextual per-resource Sarah entry (already prototyped); AI Usage meter reading a real backend-computed value, never client-calculated (AD-037).
- **Dependencies**: B9, B10, A2, A4 (contextual entry needs A4's real resources).
- **Deliverables**: Sarah chat UI wired to the real backend, matching the prototype's validated interaction shape.
- **Tests/validation**: Confirm the client never independently decides or displays a usage value it computed itself.
- **Exit criteria**: Matches the prototype's Sarah UX, against real generation.

### A10 — Activity + Notifications
- **Objective**: Updates feed + notifications UI.
- **Scope**: Per-Course-Space activity feed (already prototyped as "Updates"); notification surface (push mechanics deferred per B8/§3 — in-app feed is the priority).
- **Dependencies**: B8, A3.
- **Exit criteria**: Feed entries visibly correspond to real events, not mock data.

### A11 — Full Android/Backend Integration + Hardening
- **Objective**: The Android half of I1–I4, specific to client concerns.
- **Scope**: Error handling for real network conditions (matching the offline-first/low-bandwidth target market context already on record); crash hardening; final applicationId rename (`com.aipdfreader.app` → real namespace) if not done earlier — this was always a deferred, separate change, appropriate to finish here.
- **Dependencies**: A0–A10 complete.
- **Exit criteria**: Ready for I1.

---

## 6. Admin Web milestones

The admin app is a **client of the same backend/domain system** — it must
never introduce a second, independent business-logic or authorization
system. Every admin action is subject to the exact same AD-041/056
boundary as any other client.

### ADM-0 — Admin Web Foundation
- **Objective**: Web app scaffold + admin auth.
- **Scope**: Web application foundation; admin authentication/session (still governed by the same backend identity model as B1, not a separate one); role-aware navigation.
- **Dependencies**: B1.
- **Exit criteria**: An authenticated admin can log in; no business logic lives in this app yet.

### ADM-1 — Platform Overview
- **Objective**: Read-only visibility.
- **Scope**: Users, Cards/Course Spaces, resources/content, basic system/AI operational overview — all read paths through the real backend, no admin-only shadow queries that bypass the authorization boundary.
- **Dependencies**: ADM-0, B1–B3.
- **Exit criteria**: An admin can see real platform state.

### ADM-2 — User Management
- **Objective**: Admin-side account operations.
- **Scope**: User lookup; account state; **account-deletion workflow that triggers the exact AD-049–052 backend behavior — the admin app never implements its own deletion logic.**
- **Dependencies**: ADM-1, B11 (account deletion must already be tested backend-side).
- **Exit criteria**: Triggering deletion from admin produces identical results to the same operation happening any other way.

### ADM-3 — Content Moderation
- **Objective**: The AD-044/047 moderation workflow, exposed to admins.
- **Scope**: Shared content review; force-unshare workflow (using the real backend authorization matrix — an Owner/Admin-level admin action, not a superuser bypass); structured moderation reasons (AD-047); moderation history (via the AD-026 event log, not a separate admin-only log).
- **Dependencies**: ADM-1, B4.
- **Exit criteria**: A force-unshare from the admin app produces the same `CONTENT_FORCE_UNSHARED` event as any other force-unshare.

### ADM-4 — Course Space Administration
- **Objective**: Membership/administration visibility for support purposes.
- **Scope**: Membership inspection; administrative moderation actions bounded to what AD-034/042 already permit — no new admin-exclusive power invented here.
- **Dependencies**: ADM-1, B2.
- **Exit criteria**: Admin visibility matches real membership state exactly.

### ADM-5 — AI Operations
- **Objective**: Operational visibility into Sarah/AI usage.
- **Scope**: AI usage/limits visibility (reading the same server-authoritative counters from AD-037, not a separate accounting system); provider/health visibility; generation failure diagnostics.
- **Dependencies**: ADM-1, B9/B10.
- **Exit criteria**: Numbers shown to an admin match what the AI Router itself enforces.

### ADM-6 — Operational Tooling
- **Objective**: Support/debugging tools.
- **Scope**: Audit/event inspection (AD-026's log, read-only); system health; support tooling as genuinely needed, not speculative.
- **Dependencies**: ADM-1, B8.
- **Exit criteria**: An admin can trace "what happened" for a support request using real event data.

---

## 7. Integration + Release

### I1 — Vertical Slice Integration
- **Objective**: Connect completed backend and Android slices incrementally, not all at once at the end.
- **Scope**: After each backend milestone lands, wire its corresponding Android milestone against it for real, rather than letting Android drift ahead against mocks (the exact problem the original architecture audit found — frontend built years ahead of backend readiness. Do not repeat that pattern.).
- **Dependencies**: Ongoing, paired with B/A milestones.
- **Exit criteria**: No Android milestone is ever more than one backend milestone ahead of what it's actually wired to.

### I2 — End-to-End Workflows
- **Objective**: Validate the full user journey.
- **Scope**: The 19-step workflow specified in the governing instruction — sign in through account deletion following AD-049–052 — executed for real, on a real device/emulator against a real backend, not simulated.
- **Dependencies**: All of B0–B11, A0–A11.
- **Exit criteria**: All 19 steps pass without a human needing to explain away a failure.

### I3 — Security Validation
- **Objective**: Explicit, deliberate adversarial testing.
- **Scope**: Authorization boundaries across users, Cards, Course Spaces, artifacts, sharing, membership, and Sarah — this is not the same as I2's happy-path validation; this is specifically trying to break AD-041/044/054/055/056.
- **Dependencies**: I2.
- **Exit criteria**: Every attempted violation fails as designed.

### I4 — MVP Hardening
- **Objective**: Production-readiness that isn't premature.
- **Scope**: Reliability, error handling, performance, logging, observability, storage, configuration, migration safety, deployment readiness — matching the "excellent foundations, not shallow" philosophy already on record, without building infrastructure this product doesn't have load to justify (no Kafka, no Kubernetes, no multi-region — per the existing philosophy note).
- **Dependencies**: I3.
- **Exit criteria**: The system survives realistic load and failure conditions for an MVP, not enterprise scale.

### I5 — Production
- **Objective**: Actually ship it.
- **Scope**: Production backend, database, object storage, Android release configuration, admin deployment, monitoring, operational procedures.
- **Dependencies**: I4.
- **Exit criteria**: Real users can use it. **This milestone must not become a requirement before MVP functionality (I2/I3) is actually validated** — sequencing matters.

---

## 8. Dependency graph (summary)

```
B0 → B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8
                              ↘         ↘
                               → B9 → B10 → B11
A0 → A1(needs B1) → A2(needs B1) → A3(needs B2) → A4(needs B3)
                                  → A5(needs B5) → A6(needs B5)
                                  → A7(needs B6) → A8(needs B7)
                                  → A9(needs B9,B10) → A10(needs B8) → A11
ADM-0(needs B1) → ADM-1(needs B1-3) → ADM-2(needs B11) 
                → ADM-3(needs B4) → ADM-4(needs B2) → ADM-5(needs B9/10) → ADM-6(needs B8)
I1 (ongoing, paired) → I2 (needs everything) → I3 → I4 → I5
```

---

## 9. Explicit non-goals for this plan

- No subscription/payment infrastructure (no canonical decision requires it).
- No production-scale infrastructure ahead of validated need (no Kafka/RabbitMQ, no Kubernetes, no multi-region, no distributed caching layer) — per the standing "excellent foundations + complete MVP, not enterprise infrastructure before users exist" philosophy in `DECISIONS.md`.
- No `CourseSpace` as a separate entity (AD-019) — reaffirmed, do not revisit here.
- No one-primary-Card constraint (AD-048).
- No exact token/session architecture, generalized `ContentVersion`, `SarahSession` schema, sequence-numbered event ordering, or direct-object-storage upload mechanics — all explicitly deferred; build the minimum each milestone needs, don't design these fully now.

---

## 10. AD cross-reference (which milestone enforces which decision)

| AD(s) | Enforced primarily in |
|---|---|
| AD-019, 048 | B1, B2 |
| AD-021, 025, 030, 031, 033 | B1–B5 |
| AD-023, 032, 034, 042, 043 | B2 |
| AD-040, 041, 044, 045, 046, 047, 056 | B4, B11, I3 |
| AD-026 (+ additive event types) | B8 |
| AD-027, 028, 037, 038, 054 | B9 |
| AD-035, 036, 055 | B10 |
| AD-049–052 | B11, ADM-2, I2 |
| AD-053 | B6, B7 |

Full text of every AD remains in `docs/DECISIONS.md` — this table is a
pointer, not a duplicate.
