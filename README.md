# Lumira

Lumira is an academic productivity, resource-sharing, AI study, and social
platform for students. See the product vision doc (shared separately) for
the full picture — in short, three pillars: **Academic Workspace**
(courses, resources, notes, highlights), **Sarah** (the AI study layer,
RAG-backed and action-based), and **Community & Sharing** (Course Spaces,
direct sharing, field-of-study communities).

This repository is a monorepo with two independently-developed halves:

```
Lumira/
├── backend/    Spring Boot modular monolith (Java 21, PostgreSQL, Flyway)
└── frontend/   Android app (Kotlin, Jetpack Compose) — formerly "AIPdfReader"
```

## Backend

See [`backend/README.md`](backend/README.md) for setup, progress
(currently Step 2 of the incremental build — PostgreSQL/JPA/Flyway/Docker
Compose scaffolding), and module layout.

## Frontend

See [`frontend/README.md`](frontend/README.md) for setup and architecture.
The Android app is a thin client — no AI provider keys or logic live on
device; everything routes through the backend's AI routing layer.

## Working process

Backend and frontend are developed in separate conversations/sessions but
share this repo so each side can inspect the other's current contracts
(REST endpoints, DTOs, auth flow) as they evolve. Pull the latest `master`
before starting work on either side to stay in sync.
