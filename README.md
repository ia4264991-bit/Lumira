# Lumira Backend

Implements Vertical Slice 1 of the Lumira Platform Technical Specification
(LPTS) — Chapters 3 (University Hierarchy) and 4 (Resources). See
`docs/specification/` in the platform repository for the authoritative
specification this backend implements.

This backend is being built **incrementally, one step at a time**, per the
agreed working process. Each step is a separate commit; do not expect later
steps' functionality (database, entities, upload, Docker) to exist until
their step is reached.

## Progress

- [x] **Step 1 — Spring Boot skeleton.** Bare application, no database, no
      business logic. Proves the project compiles and runs standalone.
- [ ] Step 2 — Configure PostgreSQL and Flyway
- [ ] Step 3 — Implement Chapter 3/4 entities
- [ ] Step 4 — Implement repositories
- [ ] Step 5 — Implement REST controllers
- [ ] Step 6 — Implement local file upload
- [ ] Step 7 — Docker Compose
- [ ] Step 8 — Android integration

## Requirements (Step 1)

- Java 21
- Maven 3.9+ (or use the wrapper, once added)

No database is required for this step — that's deliberate (see `pom.xml`'s
comment on why Postgres/JPA/Flyway aren't dependencies yet).

## Running

```bash
mvn spring-boot:run
```

Then:

```bash
curl http://localhost:8080/v1/health
# {"status":"UP","service":"lumira-backend","timestamp":"..."}
```

## Testing

```bash
mvn test
```

`LumiraBackendApplicationTests` verifies the Spring context loads with no
database configured, and that `/v1/health` responds over real HTTP.

## Project layout

```
src/main/java/com/lumira/backend/
  LumiraBackendApplication.java   Entry point
  common/
    HealthController.java        Step 1's only endpoint
  (empty package scaffolding for university/course/resource/storage/user —
   populated starting Step 3; harmless if empty, Maven/Git simply ignore
   directories with no files yet)
```
