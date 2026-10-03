package com.lumira.backend.card;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "recipient_membership_id", updatable = false)
    private UUID recipientMembershipId;

    @Column(name = "recipient_user_id", updatable = false)
    private UUID recipientUserId;

    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(UUID eventId, UUID recipientMembershipId, UUID recipientUserId) {
        this.eventId = Objects.requireNonNull(eventId);
        if ((recipientMembershipId == null) == (recipientUserId == null)) {
            throw new IllegalArgumentException("Exactly one notification recipient is required");
        }
        this.recipientMembershipId = recipientMembershipId;
        this.recipientUserId = recipientUserId;
        this.deliveredAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public UUID getRecipientMembershipId() { return recipientMembershipId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
}
