# Lumira Backend

Spring Boot backend foundation for Lumira, built around the Card / Course Space / Sarah architecture (`docs/DECISIONS.md`, `docs/DOMAIN_MODEL.md`, `API_CONTRACT.md`, `docs/IMPLEMENTATION_PLAN.md`).

## Current Milestone Status: B1 (Identity + Personal Cards — code complete, tests written)

Milestone B0 (Infrastructure / Foundation) is complete. Milestone B1 adds:
- `User` entity + `V2__user_and_card.sql` migration (user + card tables, per AD-019/AD-048)
- `Card` entity, `CardRepository`, `CardService`, `CardController`
- `POST /v1/cards`, `GET /v1/cards`, `GET /v1/cards/{id}` — all `@RequireAuth`, owner-scoped
- `CardIntegrationTest` covering AD-048 (multiple cards per user), AD-056 (cross-user 403), auth boundary

B0 established:
- Spring Boot 3.3.4 + Java 21 + PostgreSQL 16+ + Spring Data JPA + Flyway.
- API error contract and envelope (`ApiErrorResponse`, `ErrorCode`, `LumiraException`, `GlobalExceptionHandler`) per `API_CONTRACT.md`.
- Foundational security boundary (`AuthenticatedUser`, `SecurityContext`, `@CurrentUser`, `CurrentUserArgumentResolver`, `SecurityInterceptor`, `TokenResolver`) ready for B1 identity implementation without premature session/auth locking.
- Domain foundation (`BaseEntity`, `ArtifactOwner` implementing `AD-057` polymorphic ownership).
- Testing infrastructure against real PostgreSQL (`BaseIntegrationTest`, `DatabaseIntegrationTest`, unit and WebMvc test suites).

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL 16+ (or Docker Compose)

## Setup

```bash
cp .env.example .env
# Edit .env or start postgres via Docker Compose / local service
```

Run application:
```bash
mvn spring-boot:run
```

Health check:
```bash
curl http://localhost:8080/v1/health
```

## Testing

Tests execute against real PostgreSQL (defaulting to `localhost:5432/lumira_test` with user `lumira_dev`):

```bash
mvn test
```

## Package Layout & Layering Conventions

```
src/main/java/com/lumira/backend/
  LumiraBackendApplication.java       Application entry point
  common/                             Cross-cutting foundations
    domain/                           Base mapped superclasses (BaseEntity, ArtifactOwner)
    error/                            ApiErrorResponse, ErrorCode, exceptions, GlobalExceptionHandler
  config/                             App-wide Spring bean and Web MVC configuration
  security/                           Identity scaffolding and authorization interceptors
  card/                               [Reserved for B1/B2] Card and Course Space domain
  resource/                           [Reserved for B3] Resource domain & processing
  study/                              [Reserved for B5-B7] Notes, Study Sets, Quizzes, Flashcards
  ai/                                 [Reserved for B9-B10] Sarah & AI router foundation
  user/                               [Reserved for B1] User identity boundary
  storage/                            Storage service abstractions
```

### Module Layering Rules
Each domain module adheres to standard layering:
- `domain`: JPA entities, domain invariants, embeddables.
- `repository`: Spring Data JPA repositories.
- `service`: Business logic, transactional boundaries (`@Transactional`).
- `controller`: REST endpoints, maps requests/responses to DTOs.
- `dto`: Request and response record objects.
