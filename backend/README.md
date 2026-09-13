# Lumira Backend

> ⚠️ **OLD ARCHITECTURE RETIRED — 2026-09-12.** This backend previously
> targeted "Vertical Slice 1" of an internal academic-hierarchy specification
> (LPTS Chapters 3–4: University/Semester/Course/CourseOffering/Resource).
> That plan, its Step 1–7 roadmap, and the domain model it described are
> **retired and no longer authoritative.** Do not resume Step 3 ("implement
> Chapter 3/4 entities") or any step after it — those steps described the
> old model.

## What actually exists right now (real, working infrastructure)

The parts of this backend that are genuinely built and unaffected by the
retirement — this is real, tested infrastructure, not architecture tied to
the old domain model:

- A Spring Boot application that compiles and runs standalone
  (`GET /v1/health`).
- PostgreSQL + Spring Data JPA + Flyway, wired and working, with one
  baseline migration (`V1__enable_pgcrypto_extension.sql`).
- Docker Compose for local Postgres + pgAdmin.
- Fully environment-variable-driven configuration (no hardcoded
  credentials, fails loudly if `DB_USER`/`DB_PASSWORD` are unset).
- Eleven empty package directories (`common/config/user/university/course/
  resource/storage/security/ai/study/search`) left over from the old
  module-boundary plan. **Their `package-info.java` files have been
  rewritten to remove old-architecture framing** — see each package for its
  current (retired/neutral) status. No entities, repositories, or
  controllers exist beyond the Step 1 health check.

None of the above requires the old domain model to be true — a database
connection, a health check, and a build pipeline are architecture-agnostic.
This is what the new architecture will build on top of.

## What is retired, not to be continued

- The Step 1–7 roadmap ("Step 3 — Implement Chapter 3/4 entities" onward).
- The `University`/`Semester`/`Course`/`CourseOffering`/`CourseOfferingMembership`/
  `Resource`/`ResourceVersion` domain model these steps targeted.
- The assumption that `docs/DECISIONS.md`'s AD-001–018 govern anything a new
  session builds here — see that file's own retirement notice.

**Do not silently pick this roadmap back up.** The next backend work should
follow whatever new architecture is derived from the UX prototype
(`docs/LUMIRA_STATE.md` tracks this), not this retired plan.

## Requirements

- Java 21
- Maven 3.9+
- Docker + Docker Compose (for Postgres/pgAdmin)

## Setup

```bash
cp .env.example .env
docker compose up -d postgres pgadmin
```

Postgres is on `localhost:5432` (or `$DB_PORT`); pgAdmin at
`http://localhost:5050` (or `$PGADMIN_PORT`) — connect to host `postgres`
(the Compose service name), port `5432`, using `DB_USER`/`DB_PASSWORD`/`DB_NAME`.

```bash
export $(cat .env | xargs)
mvn spring-boot:run
```

Flyway applies `V1__enable_pgcrypto_extension.sql` on startup.

```bash
curl http://localhost:8080/v1/health
```

## Testing

```bash
mvn test
```

Requires the same Postgres + env vars as above.

## Configuration

All database/pgAdmin credentials are environment-variable driven with no
fallback — see `.env.example`.

## Project layout

```
src/main/java/com/lumira/backend/
  LumiraBackendApplication.java   Entry point (unaffected by retirement)
  common/       Health check only — cross-cutting only, unaffected
  config/       Empty — reserved for app-wide Spring bean wiring, unaffected
  storage/      Empty — StorageService abstraction, unaffected (the
                interface/local-disk pattern here doesn't depend on the
                old domain model)
  user/         Empty — retired old-architecture scope, see package-info.java
  university/   Empty — retired old-architecture scope, see package-info.java
  course/       Empty — retired old-architecture scope, see package-info.java
  resource/     Empty — retired old-architecture scope, see package-info.java
  security/     Empty — retired old-architecture scope, see package-info.java
  ai/           Empty — retired old-architecture scope, see package-info.java
  study/        Empty — retired old-architecture scope, see package-info.java
  search/       Empty — retired old-architecture scope, see package-info.java
```

The eight packages marked "retired old-architecture scope" are empty
directories carrying only a `package-info.java` note — no real code exists
in any of them. They are left in place as a physical reminder of the prior
module boundary, not as a claim about what the new architecture's modules
should be called. The new architecture may reuse these names, rename them,
or restructure entirely — that decision hasn't been made yet.
