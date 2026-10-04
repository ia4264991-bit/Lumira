# Lumira Backend

Spring Boot backend foundation for Lumira, built around the Card / Course Space / Sarah architecture (`docs/DECISIONS.md`, `docs/DOMAIN_MODEL.md`, `API_CONTRACT.md`, `docs/IMPLEMENTATION_PLAN.md`).

## Current Milestone Status: B1 reconciliation; architecture blockers resolved, B2 next after validation

Milestone B0 (Infrastructure / Foundation) is complete. Milestone B1 adds:
- `User` entity + `V2`/`V3` migrations (`app_user` + card tables, per AD-019/AD-048/AD-057); B1 table-name/API corrections are locally unvalidated; B2 decisions are documented in AD-066 through AD-070
- `Card` entity, `CardRepository`, `CardService`, `CardController`
- `POST /v1/cards`, `GET /v1/cards`, `GET /v1/cards/{id}` — all `@RequireAuth`, owner-scoped
- `CardIntegrationTest` covering AD-048 (multiple cards per user), AD-056 (cross-user 404), auth boundary; current suite has not yet run successfully in this environment

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

### Firebase Authentication configuration (AD-082)

The non-test backend requires `LUMIRA_FIREBASE_PROJECT_ID` to name the
Firebase project that issued the Android ID tokens. It is a project
identifier, not a secret. Firebase Admin SDK credentials are loaded through
Google Application Default Credentials (ADC); no service-account file or
private key belongs in the repository or application configuration.

- **Local development:** authenticate ADC with `gcloud auth application-default
  login`, set `LUMIRA_FIREBASE_PROJECT_ID` in the process environment, then run
  the backend. A service-account key may be supplied locally through
  `GOOGLE_APPLICATION_CREDENTIALS` only if ADC via gcloud is unavailable; keep
  that file outside the repository and never commit it.

  PowerShell example:
  ```powershell
  $env:LUMIRA_FIREBASE_PROJECT_ID = "your-firebase-project-id"
  gcloud auth application-default login
  mvn spring-boot:run
  ```
- **Staging and production:** set `LUMIRA_FIREBASE_PROJECT_ID` for the matching
  Firebase project and provide ADC through the hosting platform's attached
  workload identity/service account. Do not upload a long-lived key when the
  platform supports workload identity.
- **Environment separation:** this code does not decide whether staging and
  production share a Firebase project. Configure each environment only after
  that project separation is confirmed; tokens from a different project must
  fail verification.
- **Firebase Console:** enable the Firebase Authentication providers selected
  for the client. No Firebase Admin service-account JSON is needed in Android.

Tests activate the `test` profile and use the isolated UUID foundation resolver;
the non-test runtime uses Firebase Admin verification and requires the project
ID and ADC credentials.

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
