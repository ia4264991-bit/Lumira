package com.lumira.backend.card;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID eventId, UUID cardId, String type,
        UUID actorUserId, Instant createdAt, JsonNode payload,
        UUID recipientMembershipId, UUID recipientUserId, Instant deliveredAt, Instant readAt) {
    static NotificationResponse from(Notification notification, CourseSpaceEvent event) {
        return new NotificationResponse(notification.getId(), event.getId(), event.getCardId(), event.getType(),
                event.getActorUserId(), event.getCreatedAt(), event.getPayload(),
                notification.getRecipientMembershipId(), notification.getRecipientUserId(),
                notification.getDeliveredAt(), notification.getReadAt());
    }
}
