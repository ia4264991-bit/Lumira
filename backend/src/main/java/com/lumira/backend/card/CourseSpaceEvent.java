package com.lumira.backend.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "course_space_event")
public class CourseSpaceEvent extends BaseEntity {
    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(name = "type", nullable = false, updatable = false, length = 80)
    private String type;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode payload;

    protected CourseSpaceEvent() {}

    public CourseSpaceEvent(UUID cardId, String type, UUID actorUserId, JsonNode payload) {
        this.cardId = Objects.requireNonNull(cardId);
        this.type = Objects.requireNonNull(type);
        this.actorUserId = Objects.requireNonNull(actorUserId);
        this.payload = Objects.requireNonNull(payload);
    }

    public UUID getCardId() { return cardId; }
    public String getType() { return type; }
    public UUID getActorUserId() { return actorUserId; }
    public JsonNode getPayload() { return payload; }
}
