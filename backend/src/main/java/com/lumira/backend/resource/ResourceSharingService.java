package com.lumira.backend.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.card.CourseSpaceEvent;
import com.lumira.backend.card.CourseSpaceEventRepository;
import com.lumira.backend.card.MembershipRole;
import com.lumira.backend.common.error.ConflictException;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.common.error.ValidationException;
import com.lumira.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** AD-045 Resource share lifecycle, reusing the B3 ResourceShare records. */
@Service
@Transactional
public class ResourceSharingService {
    private static final Set<String> MODERATION_REASONS = Set.of(
            "COPYRIGHT", "PRIVACY", "SAFETY", "ABUSE", "MALICIOUS_CONTENT", "POLICY_VIOLATION", "OTHER");

    private final ResourceRepository resources;
    private final ResourceShareRepository shares;
    private final CardRepository cards;
    private final CourseSpaceEventRepository events;
    private final ResourceAuthorizationService authorization;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public ResourceSharingService(ResourceRepository resources, ResourceShareRepository shares,
            CardRepository cards, CourseSpaceEventRepository events,
            ResourceAuthorizationService authorization, UserRepository users, ObjectMapper mapper) {
        this.resources = resources;
        this.shares = shares;
        this.cards = cards;
        this.events = events;
        this.authorization = authorization;
        this.users = users;
        this.mapper = mapper;
    }

    public ShareResult share(UUID resourceId, UUID cardId, UUID actorId) {
        lockActor(actorId);
        Card card = lockCourseSpace(cardId);
        authorization.requireActiveMember(card, actorId);
        Resource resource = lockResource(resourceId);
        authorization.requireResourceOwner(resource, actorId);

        ResourceShare share = shares.findByResourceIdAndCardIdForUpdate(resourceId, cardId).orElse(null);
        if (share != null && share.isActive()) return new ShareResult(share, false);
        if (share == null) share = shares.save(new ResourceShare(resourceId, cardId));
        else share.activate();
        emit(card, "ARTIFACT_SHARED", actorId, resource, "SHARE", null, null);
        return new ShareResult(share, true);
    }

    public Resource unshare(UUID resourceId, UUID cardId, UUID actorId) {
        lockActor(actorId);
        Card card = cards.findByIdForUpdate(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Space not found"));
        if (!card.isShared()) throw new ResourceNotFoundException("Course Space not found");
        Resource resource = lockResource(resourceId);
        authorization.requireResourceOwner(resource, actorId);
        ResourceShare share = shares.findByResourceIdAndCardIdForUpdate(resourceId, cardId)
                .filter(ResourceShare::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active share not found"));
        share.deactivate();
        emit(card, "CONTENT_UNSHARED", actorId, resource, "UNSHARE", null, null);
        return resource;
    }

    public Resource forceUnshare(UUID resourceId, UUID cardId, UUID actorId,
            String reason, String note) {
        lockActor(actorId);
        Card card = lockCourseSpace(cardId);
        MembershipRole role = authorization.requireOwnerOrAdmin(card, actorId);
        if (reason != null && !MODERATION_REASONS.contains(reason)) {
            throw new ValidationException("Unsupported moderation reason");
        }
        if (role == MembershipRole.ADMIN && (reason == null || reason.isBlank())) {
            throw new ValidationException("An active Admin must supply a moderation reason");
        }
        if (note != null && note.length() > 2000) {
            throw new ValidationException("Moderation note must be 2000 characters or fewer");
        }

        Resource resource = lockResource(resourceId);
        ResourceShare share = shares.findByResourceIdAndCardIdForUpdate(resourceId, cardId)
                .filter(ResourceShare::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active share not found"));
        share.deactivate();
        emit(card, "CONTENT_FORCE_UNSHARED", actorId, resource, "FORCE_UNSHARE", reason, note);
        return resource;
    }

    private Card lockCourseSpace(UUID cardId) {
        Card card = cards.findByIdForUpdate(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Space not found"));
        if (!card.isShared()) throw new ResourceNotFoundException("Course Space not found");
        return card;
    }

    private void lockActor(UUID actorId) {
        if (users.findByIdForUpdate(actorId).isEmpty()) {
            throw new ResourceNotFoundException("Account not found");
        }
    }

    private Resource lockResource(UUID resourceId) {
        return resources.findByIdForUpdate(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
    }

    private void emit(Card card, String type, UUID actorId, Resource resource,
            String operation, String reason, String note) {
        ObjectNode payload = mapper.createObjectNode()
                .put("artifactType", "resource")
                .put("artifactId", resource.getId().toString())
                .put("resourceId", resource.getId().toString())
                .put("operation", operation);
        UUID ownerUserId = resource.getOwner().isUserOwned()
                ? resource.getOwner().getOwningUserId()
                : cards.findById(resource.getOwner().getOwningCardId()).map(Card::getOwnerId).orElse(null);
        if (ownerUserId != null) payload.put("artifactOwnerUserId", ownerUserId.toString());
        if (type.equals("CONTENT_FORCE_UNSHARED")) {
            if (reason == null) payload.putNull("reason"); else payload.put("reason", reason);
            if (note == null || note.isBlank()) payload.putNull("note"); else payload.put("note", note.strip());
        }
        events.save(new CourseSpaceEvent(card.getId(), type, actorId, payload));
    }

    public record ShareResult(ResourceShare share, boolean changed) { }
}
