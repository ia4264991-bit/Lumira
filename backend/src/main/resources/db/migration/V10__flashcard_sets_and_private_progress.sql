CREATE TABLE flashcard_set (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_card_id UUID REFERENCES card(id) ON DELETE CASCADE,
    owner_user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT flashcard_set_exactly_one_owner CHECK (num_nonnulls(owner_card_id, owner_user_id) = 1),
    CONSTRAINT flashcard_set_title_nonblank CHECK (length(btrim(title)) > 0)
);
CREATE INDEX flashcard_set_owner_card_created_idx ON flashcard_set(owner_card_id, created_at DESC) WHERE owner_card_id IS NOT NULL;
CREATE INDEX flashcard_set_owner_user_created_idx ON flashcard_set(owner_user_id, created_at DESC) WHERE owner_user_id IS NOT NULL;

CREATE TABLE flashcard (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flashcard_set_id UUID NOT NULL REFERENCES flashcard_set(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position > 0),
    front TEXT NOT NULL,
    back TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT flashcard_set_position_unique UNIQUE (flashcard_set_id, position)
);
CREATE INDEX flashcard_set_position_idx ON flashcard(flashcard_set_id, position, id);

CREATE TABLE flashcard_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    flashcard_id UUID NOT NULL REFERENCES flashcard(id) ON DELETE CASCADE,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('AGAIN', 'GOT_IT')),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT flashcard_progress_user_card_unique UNIQUE (user_id, flashcard_id)
);
CREATE INDEX flashcard_progress_user_reviewed_idx ON flashcard_progress(user_id, reviewed_at DESC);

ALTER TABLE artifact_share ADD COLUMN flashcard_set_id UUID REFERENCES flashcard_set(id) ON DELETE CASCADE;
ALTER TABLE artifact_share DROP CONSTRAINT artifact_share_exactly_one_artifact;
ALTER TABLE artifact_share ADD CONSTRAINT artifact_share_exactly_one_artifact
    CHECK (num_nonnulls(resource_id, note_id, study_set_id, quiz_id, flashcard_set_id) = 1);
CREATE UNIQUE INDEX artifact_share_flashcard_set_card_unique
    ON artifact_share(flashcard_set_id, card_id) WHERE flashcard_set_id IS NOT NULL;
