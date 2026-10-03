package com.lumira.backend.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared JPA embeddable for polymorphic artifact ownership per AD-057.
 *
 * <p>Represents ownership by either a Card or directly by a User.
 * Exactly one of {@code owningCardId} or {@code owningUserId} must be non-null.
 */
@Embeddable
public class ArtifactOwner implements Serializable {

    @Column(name = "owner_card_id")
    private UUID owningCardId;

    @Column(name = "owner_user_id")
    private UUID owningUserId;

    public ArtifactOwner() {
    }

    private ArtifactOwner(UUID owningCardId, UUID owningUserId) {
        this.owningCardId = owningCardId;
        this.owningUserId = owningUserId;
        validate();
    }

    public static ArtifactOwner forCard(UUID cardId) {
        Objects.requireNonNull(cardId, "cardId cannot be null");
        return new ArtifactOwner(cardId, null);
    }

    public static ArtifactOwner forUser(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return new ArtifactOwner(null, userId);
    }

    public boolean isCardOwned() {
        return owningCardId != null && owningUserId == null;
    }

    public boolean isUserOwned() {
        return owningUserId != null && owningCardId == null;
    }

    public void validate() {
        int nonNullCount = (owningCardId != null ? 1 : 0) + (owningUserId != null ? 1 : 0);
        if (nonNullCount != 1) {
            throw new IllegalStateException(
                    "AD-057 invariant violation: exactly one of owningCardId or owningUserId must be non-null. Found owningCardId="
                            + owningCardId + ", owningUserId=" + owningUserId
            );
        }
    }

    public UUID getOwningCardId() {
        return owningCardId;
    }

    public void setOwningCardId(UUID owningCardId) {
        this.owningCardId = owningCardId;
    }

    public UUID getOwningUserId() {
        return owningUserId;
    }

    public void setOwningUserId(UUID owningUserId) {
        this.owningUserId = owningUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactOwner that = (ArtifactOwner) o;
        return Objects.equals(owningCardId, that.owningCardId) && Objects.equals(owningUserId, that.owningUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owningCardId, owningUserId);
    }

    @Override
    public String toString() {
        return "ArtifactOwner{" +
                "owningCardId=" + owningCardId +
                ", owningUserId=" + owningUserId +
                '}';
    }
}
