package com.lumira.backend.resource;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "resource_share")
public class ResourceShare extends BaseEntity {
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;
    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;
    @Column(name = "active", nullable = false)
    private boolean active;

    protected ResourceShare() { }
    public ResourceShare(UUID resourceId, UUID cardId) {
        this.resourceId = resourceId;
        this.cardId = cardId;
        this.active = true;
    }
    public UUID getResourceId() { return resourceId; }
    public UUID getCardId() { return cardId; }
    public boolean isActive() { return active; }
    public void activate() { active = true; }
    public void deactivate() { active = false; }
}
