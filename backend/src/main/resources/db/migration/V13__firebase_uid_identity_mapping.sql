-- AD-082: retain existing unlinked app_user rows while adding the durable,
-- one-to-one Firebase UID mapping. PostgreSQL permits multiple NULL values
-- under a UNIQUE constraint, while linked UIDs are unique.
ALTER TABLE app_user
    ADD COLUMN firebase_uid VARCHAR(128);

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_firebase_uid UNIQUE (firebase_uid);
