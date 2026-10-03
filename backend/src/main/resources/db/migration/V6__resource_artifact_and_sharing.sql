CREATE TABLE resource (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_card_id UUID REFERENCES card(id) ON DELETE CASCADE,
    owner_user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    title VARCHAR(240) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    mime_type VARCHAR(160) NOT NULL,
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes >= 0),
    processing_status VARCHAR(16) NOT NULL CHECK (processing_status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    failure_reason VARCHAR(1000),
    original_bytes BYTEA,
    extracted_content JSONB NOT NULL DEFAULT '[]'::jsonb,
    image_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT resource_exactly_one_owner CHECK (num_nonnulls(owner_card_id, owner_user_id) = 1),
    CONSTRAINT resource_failure_reason_consistent CHECK
        ((processing_status = 'FAILED' AND failure_reason IS NOT NULL AND length(btrim(failure_reason)) > 0)
          OR (processing_status <> 'FAILED' AND failure_reason IS NULL))
);

CREATE INDEX resource_owner_card_created_idx ON resource(owner_card_id, created_at DESC)
    WHERE owner_card_id IS NOT NULL;
CREATE INDEX resource_owner_user_created_idx ON resource(owner_user_id, created_at DESC)
    WHERE owner_user_id IS NOT NULL;

-- AD-045 share records are independent of artifact ownership. B3 only needs
-- the Resource specialization; other artifact share records belong to B4.
CREATE TABLE resource_share (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT resource_share_one_per_pair UNIQUE (resource_id, card_id)
);

CREATE INDEX resource_share_active_card_idx ON resource_share(card_id, resource_id)
    WHERE active = TRUE;
