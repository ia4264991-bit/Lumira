-- AD-052: account deletion must retain append-only CourseSpaceEvent history,
-- including events whose actor or former Course Space Card is deleted.
-- The UUIDs remain as historical identifiers; they are no longer live FKs.
ALTER TABLE course_space_event
    DROP CONSTRAINT IF EXISTS course_space_event_actor_user_id_fkey;

ALTER TABLE course_space_event
    DROP CONSTRAINT IF EXISTS course_space_event_card_id_fkey;

CREATE INDEX resource_share_active_successor_idx
    ON resource_share(resource_id, created_at ASC, id ASC)
    WHERE active = TRUE;
