-- V4 used a CASE expression to read fields from both trigger row types.
-- PostgreSQL resolves NEW/OLD record fields against the actual triggering
-- table, so the other table's field caused every Card or membership commit to
-- fail. Branch by table and operation before reading the corresponding row.
CREATE OR REPLACE FUNCTION assert_shared_card_owner_membership() RETURNS trigger AS $$
DECLARE
    target_card_id UUID;
    current_owner_id UUID;
    shared BOOLEAN;
    matching_owner_count INTEGER;
    invalid_member_card_count INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'card' THEN
        IF TG_OP = 'DELETE' THEN
            target_card_id := OLD.id;
        ELSE
            target_card_id := NEW.id;
        END IF;
    ELSE
        IF TG_OP = 'DELETE' THEN
            target_card_id := OLD.card_id;
        ELSE
            target_card_id := NEW.card_id;
        END IF;
    END IF;

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
