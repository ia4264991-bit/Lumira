package com.lumira.backend.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The sole transactional path for appending Course Space events and deriving notifications. */
@Service
@Transactional
public class CourseSpaceEventService {
    private final CourseSpaceEventRepository events;
    private final CardMembershipRepository memberships;
    private final NotificationRepository notifications;
    private final ObjectMapper mapper;

    public CourseSpaceEventService(CourseSpaceEventRepository events, CardMembershipRepository memberships,
            NotificationRepository notifications, ObjectMapper mapper) {
        this.events = events;
        this.memberships = memberships;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    public CourseSpaceEvent emit(UUID cardId, String type, UUID actorUserId, Object payloadValue) {
        var payload = mapper.valueToTree(payloadValue);
        CourseSpaceEvent event = events.saveAndFlush(new CourseSpaceEvent(cardId, type, actorUserId, payload));
        List<CardMembership> active = memberships.findByCardIdAndStatusOrderByCreatedAtAsc(
                cardId, MembershipStatus.ACTIVE);
        Map<String, Notification> recipients = new LinkedHashMap<>();
        UUID eventId = event.getId();

        switch (type) {
            case "RESOURCE_ADDED", "ARTIFACT_SHARED", "MEMBER_JOINED", "MEMBER_LEFT",
                    "ANNOUNCEMENT_POSTED", "CONTENT_UNSHARED", "COURSE_SPACE_DISSOLVED",
                    "MEMBER_INVITATION_ACCEPTED" -> addActiveExceptActor(recipients, eventId, active, actorUserId);
            case "MEMBER_REMOVED" -> {
                addActiveExceptActor(recipients, eventId, active, actorUserId);
                addPayloadMembership(recipients, eventId, cardId, payload, "membershipId", "userId");
            }
            case "MEMBER_PROMOTED", "MEMBER_DEMOTED" -> {
                addActiveExceptActor(recipients, eventId, active, actorUserId);
                addPayloadMembership(recipients, eventId, cardId, payload, "membershipId", "userId");
            }
            case "OWNERSHIP_TRANSFERRED" -> {
                addActiveExceptActor(recipients, eventId, active, actorUserId);
                addPayloadMembership(recipients, eventId, cardId, payload, "fromMembershipId", "fromUserId");
                addPayloadMembership(recipients, eventId, cardId, payload, "toMembershipId", "toUserId");
            }
            case "MEMBER_INVITED", "MEMBER_INVITATION_WITHDRAWN" ->
                    addPayloadMembership(recipients, eventId, cardId, payload, "membershipId", "userId");
            case "MEMBER_INVITATION_DECLINED" -> active.stream()
                    .filter(m -> !m.getUserId().equals(actorUserId))
                    .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.ADMIN)
                    .forEach(m -> addMembership(recipients, eventId, m));
            case "CONTENT_FORCE_UNSHARED" -> {
                addActiveExceptActor(recipients, eventId, active, actorUserId);
                UUID ownerUserId = userId(payload, "artifactOwnerUserId");
                CardMembership ownerMembership = active.stream()
                        .filter(m -> m.getUserId().equals(ownerUserId)).findFirst().orElse(null);
                if (ownerMembership == null) addDirectUser(recipients, eventId, ownerUserId);
                else addMembership(recipients, eventId, ownerMembership);
            }
            default -> { /* Open event types without a B8 notification policy remain feed-only. */ }
        }

        if (!recipients.isEmpty()) notifications.saveAll(recipients.values());
        return event;
    }

    private void addActiveExceptActor(Map<String, Notification> recipients, UUID eventId,
            List<CardMembership> active, UUID actorId) {
        active.stream().filter(m -> !m.getUserId().equals(actorId))
                .forEach(m -> addMembership(recipients, eventId, m));
    }

    private void addLatestMembership(Map<String, Notification> recipients, UUID eventId, UUID cardId, UUID userId) {
        List<CardMembership> episodes = memberships.findByCardIdAndUserIdOrderByCreatedAtAsc(cardId, userId);
        if (episodes.isEmpty()) throw new IllegalStateException("Event recipient membership is missing");
        addMembership(recipients, eventId, episodes.getLast());
    }

    private void addPayloadMembership(Map<String, Notification> recipients, UUID eventId, UUID cardId,
            com.fasterxml.jackson.databind.JsonNode payload, String membershipKey, String userKey) {
        if (payload.hasNonNull(membershipKey)) {
            UUID membershipId = UUID.fromString(payload.get(membershipKey).asText());
            CardMembership membership = memberships.findById(membershipId)
                    .orElseThrow(() -> new IllegalStateException("Event recipient membership is missing"));
            addMembership(recipients, eventId, membership);
            return;
        }
        addLatestMembership(recipients, eventId, cardId, userId(payload, userKey));
    }

    private void addMembership(Map<String, Notification> recipients, UUID eventId, CardMembership membership) {
        recipients.putIfAbsent("membership:" + membership.getId(), new Notification(eventId, membership.getId(), null));
    }

    private void addDirectUser(Map<String, Notification> recipients, UUID eventId, UUID userId) {
        recipients.putIfAbsent("user:" + userId, new Notification(eventId, null, userId));
    }

    private UUID userId(com.fasterxml.jackson.databind.JsonNode payload, String key) {
        if (payload == null || payload.get(key) == null || payload.get(key).isNull()) {
            throw new IllegalStateException("Event payload is missing required recipient field " + key);
        }
        return UUID.fromString(payload.get(key).asText());
    }
}
