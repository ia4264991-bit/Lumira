-- AD-057 requires real foreign keys to app_user for user-owned artifacts.
-- Rename the existing table so PostgreSQL preserves its data and references.
ALTER TABLE "user" RENAME TO app_user;
