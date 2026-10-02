package com.lumira.backend.card;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Card entity — the sole workspace entity in the Lumira domain (AD-019).
 *
 * <p>A Card is both a personal workspace and, when {@code isShared} is true,
 * a Course Space. No separate CourseSpace table exists (AD-019 reaffirmed).
 *
 * <p>AD-048: a User may own zero or more Cards. No one-primary-Card constraint.
 *
 * <p>Persistence schema: {@code card} table — {@code id, owner_id, name, color,
 * is_shared, created_at}.
 */
@Entity
@Table(name = "card")
public class Card extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "color")
    private String color;

    /** Whether this Card is currently acting as a Course Space (AD-019). */
    @Column(name = "is_shared", nullable = false)
    private boolean isShared;

    @Column(name = "invite_token", unique = true)
    private String inviteToken;

    @Column(name = "invite_token_version", nullable = false)
    private long inviteTokenVersion;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval;

    protected Card() {
        // JPA
    }

    public Card(UUID ownerId, String name, String color) {
        Objects.requireNonNull(ownerId, "ownerId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Card name cannot be blank");
        }
        this.ownerId = ownerId;
        this.name = name.strip();
        this.color = color != null ? color.strip() : null;
        this.isShared = false;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Card name cannot be blank");
        }
        this.name = name.strip();
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color != null ? color.strip() : null;
    }

    public boolean isShared() {
        return isShared;
    }

    /** Package-private — sharing state is managed by the Course Space lifecycle (B2). */
    void setShared(boolean shared) {
        isShared = shared;
    }

    public String getInviteToken() { return inviteToken; }

    public long getInviteTokenVersion() { return inviteTokenVersion; }

    public boolean isRequireApproval() { return requireApproval; }

    void resetInviteLink(String token) {
        this.inviteToken = Objects.requireNonNull(token, "token cannot be null");
        this.inviteTokenVersion++;
    }

    void setRequireApproval(boolean requireApproval) {
        this.requireApproval = requireApproval;
    }

    void transferOwnership(UUID newOwnerId) {
        this.ownerId = Objects.requireNonNull(newOwnerId, "newOwnerId cannot be null");
    }

    /**
     * Returns true if {@code userId} is the owner of this Card.
     * Ownership enforcement per AD-041 — ownership is never inferred from object
     * existence or membership.
     */
    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }
}
