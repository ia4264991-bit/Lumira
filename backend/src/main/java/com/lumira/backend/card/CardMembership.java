package com.lumira.backend.card;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "card_membership")
@AttributeOverride(name = "createdAt", column = @Column(name = "joined_at", nullable = false, updatable = false))
public class CardMembership extends BaseEntity {

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "member_card_id", nullable = false)
    private UUID memberCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MembershipStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MembershipRole role;

    protected CardMembership() {}

    public CardMembership(UUID cardId, UUID userId, UUID memberCardId,
                          MembershipStatus status, MembershipRole role) {
        this.cardId = Objects.requireNonNull(cardId);
        this.userId = Objects.requireNonNull(userId);
        this.memberCardId = Objects.requireNonNull(memberCardId);
        this.status = Objects.requireNonNull(status);
        this.role = Objects.requireNonNull(role);
    }

    public UUID getCardId() { return cardId; }
    public UUID getUserId() { return userId; }
    public UUID getMemberCardId() { return memberCardId; }
    public MembershipStatus getStatus() { return status; }
    public MembershipRole getRole() { return role; }

    public boolean isCurrent() {
        return status == MembershipStatus.ACTIVE || status == MembershipStatus.INVITED;
    }

    public boolean isActive() { return status == MembershipStatus.ACTIVE; }

    public void accept() {
        requireStatus(MembershipStatus.INVITED);
        status = MembershipStatus.ACTIVE;
    }

    public void decline() {
        requireStatus(MembershipStatus.INVITED);
        status = MembershipStatus.LEFT;
    }

    public void withdraw() {
        requireStatus(MembershipStatus.INVITED);
        status = MembershipStatus.REMOVED;
    }

    public void leave() {
        requireStatus(MembershipStatus.ACTIVE);
        if (role == MembershipRole.OWNER) throw new IllegalStateException("Owner cannot leave before transfer or dissolution");
        status = MembershipStatus.LEFT;
    }

    public void remove() {
        requireStatus(MembershipStatus.ACTIVE);
        if (role == MembershipRole.OWNER) throw new IllegalStateException("Owner cannot be removed");
        status = MembershipStatus.REMOVED;
    }

    public void promote() {
        if (!isActive() || role != MembershipRole.MEMBER) throw new IllegalStateException("Only an active Member can be promoted");
        role = MembershipRole.ADMIN;
    }

    public void demote() {
        if (!isActive() || role != MembershipRole.ADMIN) throw new IllegalStateException("Only an active Admin can be demoted");
        role = MembershipRole.MEMBER;
    }

    public void setRole(MembershipRole role) { this.role = Objects.requireNonNull(role); }

    public void setMemberCardId(UUID memberCardId) { this.memberCardId = Objects.requireNonNull(memberCardId); }

    private void requireStatus(MembershipStatus expected) {
        if (status != expected) throw new IllegalStateException("Membership is not " + expected);
    }
}
