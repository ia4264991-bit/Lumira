# Lumira Backend

Implements Vertical Slice 1 of the Lumira Platform Technical Specification
(LPTS) — Chapters 3 (University Hierarchy) and 4 (Resources). See
`docs/specification/` in the platform repository for the authoritative
specification this backend implements.

This backend is being built **incrementally, one step at a time**, per the
agreed working process. Each step is a separate commit; do not expect later
steps' functionality (entities, repositories, controllers, upload, Android
integration) to exist until their step is reached.

## Progress

- [x] **Step 1 — Spring Boot skeleton.** Bare application, no database, no
      business logic. Proves the project compiles and runs standalone.
- [x] **Step 2 — PostgreSQL, Spring Data JPA, Flyway, Docker Compose.**
      Real datasource, env-var-driven configuration, one baseline
      migration, Postgres + pgAdmin via Docker Compose. Also established
      all eleven module package boundaries
      (`common/config/user/university/course/resource/storage/security/ai/study/search`)
      with `package-info.java` documenting each module's scope and — just
      as importantly — what it explicitly does *not* own. Still no
      entities, repositories, or controllers beyond Step 1's health check.
- [ ] Step 3 — Implement Chapter 3/4 entities
- [ ] Step 4 — Implement repositories
- [ ] Step 5 — Implement REST controllers
- [ ] Step 6 — Implement local file upload
- [ ] Step 7 — Android integration

## Requirements

- Java 21
- Maven 3.9+
- Docker + Docker Compose (for Postgres/pgAdmin — see below)

## Setup

```bash
cp .env.example .env
# edit .env if you want different local credentials — the placeholders
# work fine for local development as-is

docker compose up -d postgres pgadmin
```

Postgres is on `localhost:5432` (or `$DB_PORT`); pgAdmin's web UI is at
`http://localhost:5050` (or `$PGADMIN_PORT`) — log in with the
`PGADMIN_EMAIL`/`PGADMIN_PASSWORD` from your `.env`, then add a server
connection pointing at host `postgres` (the Docker service name, not
`localhost`, since pgAdmin reaches it over the Compose network),
port `5432`, using `DB_USER`/`DB_PASSWORD`/`DB_NAME`.

Then, with the same variables available in your shell (`export $(cat .env
| xargs)` or your IDE's run-configuration env vars):

```bash
mvn spring-boot:run
```

On startup, Flyway applies `V1__enable_pgcrypto_extension.sql` — check the
logs for `Successfully applied 1 migration`, or inspect the
`flyway_schema_history` table in pgAdmin.

```bash
curl http://localhost:8080/v1/health
# {"status":"UP","service":"lumira-backend","timestamp":"..."}
```

## Testing

```bash
mvn test
```

Requires the same Postgres + env vars as above — see the note at the top of
`LumiraBackendApplicationTests` for why this is now a real integration test
rather than the zero-dependency one from Step 1.

## Configuration

All database and pgAdmin credentials are environment-variable driven, with
**no fallback for username/password** — `application.yml` and
`docker-compose.yml` both fail loudly if `DB_USER`/`DB_PASSWORD` aren't set,
rather than silently running against a baked-in development credential.
See `.env.example` for the full variable list.

## Project layout

```
src/main/java/com/lumira/backend/
  LumiraBackendApplication.java   Entry point
  common/       Health check only so far — see package-info.java for scope
  config/       Empty — reserved for app-wide Spring bean wiring
  user/         Empty — minimal User entity lands in Step 3
  university/   Empty — University/Semester (Chapter 3) land in Step 3
  course/       Empty — Course/CourseOffering/Membership (Chapter 3) land in Step 3
  resource/     Empty — Resource/ResourceVersion (Chapter 4) land in Step 3+
  storage/      Empty — StorageService abstraction lands in Step 6
  security/     Empty — reserved for LPTS Chapter 15, out of scope this slice
  ai/           Empty — reserved for LPTS Chapters 7–9, out of scope this slice
  study/        Empty — reserved for LPTS Chapter 5, out of scope this slice
  search/       Empty — reserved for LPTS Chapter 11, out of scope this slice

  Every package above has a package-info.java stating its scope and,
  explicitly, what it does not own — read those before adding a class to
  make sure it's going in the right module.
```
