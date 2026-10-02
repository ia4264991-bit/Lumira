-- B2: Course Spaces are shared Cards; never a separate workspace entity.
ALTER TABLE card
    ADD COLUMN invite_token VARCHAR(128),
    ADD COLUMN invite_token_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN require_approval BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_card_invite_token ON card(invite_token) WHERE invite_token IS NOT NULL;

CREATE TABLE card_membership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES card(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    member_card_id UUID NOT NULL REFERENCES card(id),
    status VARCHAR(16) NOT NULL,
    role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_card_membership_status CHECK (status IN ('INVITED', 'ACTIVE', 'LEFT', 'REMOVED')),
    CONSTRAINT chk_card_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT chk_owner_membership_active CHECK (role <> 'OWNER' OR status = 'ACTIVE')
);

CREATE UNIQUE INDEX uq_card_membership_current
    ON card_membership(card_id, user_id) WHERE status IN ('ACTIVE', 'INVITED');
CREATE UNIQUE INDEX uq_card_membership_owner
    ON card_membership(card_id) WHERE status = 'ACTIVE' AND role = 'OWNER';
CREATE INDEX idx_card_membership_user_status ON card_membership(user_id, status);
CREATE INDEX idx_card_membership_member_card ON card_membership(member_card_id);

CREATE TABLE card_join_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES card(id),
    requesting_user_id UUID NOT NULL REFERENCES app_user(id),
    invite_token_version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by_user_id UUID REFERENCES app_user(id),
    CONSTRAINT chk_card_join_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'INVALIDATED')),
    CONSTRAINT chk_card_join_request_resolution CHECK ((status = 'PENDING' AND resolved_at IS NULL AND resolved_by_user_id IS NULL) OR (status <> 'PENDING' AND resolved_at IS NOT NULL))
);

CREATE INDEX idx_card_join_request_pending ON card_join_request(card_id, status, created_at);
CREATE INDEX idx_card_join_request_user ON card_join_request(requesting_user_id, created_at DESC);

CREATE TABLE course_space_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES card(id),
    type VARCHAR(80) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_course_space_event_card_created ON course_space_event(card_id, created_at DESC);

CREATE FUNCTION reject_course_space_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Course Space events are append-only'
        USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_course_space_event_append_only
    BEFORE UPDATE OR DELETE ON course_space_event
    FOR EACH ROW EXECUTE FUNCTION reject_course_space_event_mutation();

-- Deferred checks allow share enablement and owner transfer to update the
-- Card and its owner membership in one transaction, while rejecting any
-- committed shared Card without exactly one matching active Owner.
CREATE FUNCTION assert_shared_card_owner_membership() RETURNS trigger AS $$
DECLARE
    target_card_id UUID;
    current_owner_id UUID;
    shared BOOLEAN;
    matching_owner_count INTEGER;
    invalid_member_card_count INTEGER;
BEGIN
    target_card_id := CASE WHEN TG_TABLE_NAME = 'card' THEN COALESCE(NEW.id, OLD.id)
                           ELSE COALESCE(NEW.card_id, OLD.card_id) END;
    SELECT c.owner_id, c.is_shared INTO current_owner_id, shared
      FROM card c WHERE c.id = target_card_id;
    IF FOUND AND shared THEN
        SELECT count(*) INTO matching_owner_count
          FROM card_membership m
         WHERE m.card_id = target_card_id
           AND m.status = 'ACTIVE'
           AND m.role = 'OWNER'
           AND m.user_id = current_owner_id
           AND m.member_card_id = target_card_id;
        IF matching_owner_count <> 1 THEN
            RAISE EXCEPTION 'shared Card % must have exactly one matching active Owner membership', target_card_id
                USING ERRCODE = '23514';
        END IF;
    END IF;
    SELECT count(*) INTO invalid_member_card_count
      FROM card_membership m
      JOIN card member_card ON member_card.id = m.member_card_id
     WHERE (m.card_id = target_card_id OR m.member_card_id = target_card_id)
       AND member_card.owner_id <> m.user_id;
    IF invalid_member_card_count <> 0 THEN
        RAISE EXCEPTION 'Card memberships for % must reference Cards owned by their member', target_card_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_shared_card_owner_membership_card
    AFTER INSERT OR UPDATE OF owner_id, is_shared ON card
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_shared_card_owner_membership();

CREATE CONSTRAINT TRIGGER trg_shared_card_owner_membership_membership
    AFTER INSERT OR UPDATE OR DELETE ON card_membership
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_shared_card_owner_membership();
