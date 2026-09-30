-- B1: Identity + Personal Cards
-- AD-019: card table (id, ownerId, name, color, isShared, createdAt)
-- AD-048: no one-primary-card constraint — zero or more cards per user

CREATE TABLE "user" (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email      TEXT        NOT NULL,
    display_name TEXT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_email UNIQUE (email)
);

CREATE TABLE card (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    owner_id   UUID        NOT NULL REFERENCES "user"(id),
    name       TEXT        NOT NULL,
    color      TEXT,
    is_shared  BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_card_name_not_blank CHECK (char_length(trim(name)) > 0)
);

CREATE INDEX idx_card_owner_id ON card(owner_id);
