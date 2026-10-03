CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES course_space_event(id) ON DELETE RESTRICT,
    recipient_membership_id UUID REFERENCES card_membership(id) ON DELETE CASCADE,
    recipient_user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notification_exactly_one_recipient
        CHECK (num_nonnulls(recipient_membership_id, recipient_user_id) = 1)
);

CREATE UNIQUE INDEX notification_event_membership_unique
    ON notification(event_id, recipient_membership_id)
    WHERE recipient_membership_id IS NOT NULL;
CREATE UNIQUE INDEX notification_event_user_unique
    ON notification(event_id, recipient_user_id)
    WHERE recipient_user_id IS NOT NULL;
CREATE INDEX notification_membership_inbox_idx
    ON notification(recipient_membership_id, delivered_at DESC, id);
CREATE INDEX notification_user_inbox_idx
    ON notification(recipient_user_id, delivered_at DESC, id)
    WHERE recipient_user_id IS NOT NULL;
