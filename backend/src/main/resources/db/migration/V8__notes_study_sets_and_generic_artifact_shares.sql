CREATE TABLE note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_card_id UUID REFERENCES card(id) ON DELETE CASCADE,
    owner_user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT note_exactly_one_owner CHECK (num_nonnulls(owner_card_id, owner_user_id) = 1)
);
CREATE INDEX note_owner_card_created_idx ON note(owner_card_id, created_at DESC) WHERE owner_card_id IS NOT NULL;
CREATE INDEX note_owner_user_created_idx ON note(owner_user_id, created_at DESC) WHERE owner_user_id IS NOT NULL;

CREATE TABLE study_set (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_card_id UUID REFERENCES card(id) ON DELETE CASCADE,
    owner_user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT study_set_exactly_one_owner CHECK (num_nonnulls(owner_card_id, owner_user_id) = 1)
);
CREATE INDEX study_set_owner_card_created_idx ON study_set(owner_card_id, created_at DESC) WHERE owner_card_id IS NOT NULL;
CREATE INDEX study_set_owner_user_created_idx ON study_set(owner_user_id, created_at DESC) WHERE owner_user_id IS NOT NULL;

-- Generalize B3's explicit share records in place. Each artifact reference
-- remains a real FK; exactly one artifact type is referenced per share row.
ALTER TABLE resource_share RENAME TO artifact_share;
ALTER TABLE artifact_share DROP CONSTRAINT resource_share_one_per_pair;
ALTER TABLE artifact_share ALTER COLUMN resource_id DROP NOT NULL;
ALTER TABLE artifact_share ADD COLUMN note_id UUID REFERENCES note(id) ON DELETE CASCADE;
ALTER TABLE artifact_share ADD COLUMN study_set_id UUID REFERENCES study_set(id) ON DELETE CASCADE;
ALTER TABLE artifact_share ADD CONSTRAINT artifact_share_exactly_one_artifact
    CHECK (num_nonnulls(resource_id, note_id, study_set_id) = 1);
CREATE UNIQUE INDEX artifact_share_resource_card_unique ON artifact_share(resource_id, card_id) WHERE resource_id IS NOT NULL;
CREATE UNIQUE INDEX artifact_share_note_card_unique ON artifact_share(note_id, card_id) WHERE note_id IS NOT NULL;
CREATE UNIQUE INDEX artifact_share_study_set_card_unique ON artifact_share(study_set_id, card_id) WHERE study_set_id IS NOT NULL;
DROP INDEX resource_share_active_card_idx;
CREATE INDEX artifact_share_active_card_idx ON artifact_share(card_id, created_at) WHERE active = TRUE;
