package com.lumira.backend.card;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "card_join_request")
public class CardJoinRequest extends BaseEntity {

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(name = "requesting_user_id", nullable = false, updatable = false)
    private UUID requestingUserId;

    @Column(name = "invite_token_version", nullable = false, updatable = false)
    private long inviteTokenVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JoinRequestStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    protected CardJoinRequest() {}

    public CardJoinRequest(UUID cardId, UUID requestingUserId, long inviteTokenVersion) {
        this.cardId = Objects.requireNonNull(cardId);
        this.requestingUserId = Objects.requireNonNull(requestingUserId);
        this.inviteTokenVersion = inviteTokenVersion;
        this.status = JoinRequestStatus.PENDING;
    }

    public UUID getCardId() { return cardId; }
    public UUID getRequestingUserId() { return requestingUserId; }
    public long getInviteTokenVersion() { return inviteTokenVersion; }
    public JoinRequestStatus getStatus() { return status; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedByUserId() { return resolvedByUserId; }

    public void resolve(JoinRequestStatus newStatus, UUID actorId) {
        if (status != JoinRequestStatus.PENDING) throw new IllegalStateException("Join request is not pending");
        if (newStatus == JoinRequestStatus.PENDING) throw new IllegalArgumentException("Resolution must be terminal");
        status = newStatus;
        resolvedAt = Instant.now();
        resolvedByUserId = Objects.requireNonNull(actorId);
    }
}
