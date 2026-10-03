package com.lumira.backend.resource;

import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardMembershipRepository;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.card.CourseSpaceEventService;
import com.lumira.backend.card.MembershipRole;
import com.lumira.backend.card.MembershipStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Atomically commits the final Resource representation with its share and Course Space event. */
@Service
public class ResourcePublicationService {
    private final ResourceRepository resources;
    private final ResourceShareRepository shares;
    private final CardRepository cards;
    private final CardMembershipRepository memberships;
    private final CourseSpaceEventService eventService;

    public ResourcePublicationService(ResourceRepository resources, ResourceShareRepository shares,
            CardRepository cards, CardMembershipRepository memberships, CourseSpaceEventService eventService) {
        this.resources = resources;
        this.shares = shares;
        this.cards = cards;
        this.memberships = memberships;
        this.eventService = eventService;
    }

    @Transactional
    public void completeUpload(Resource resource, UUID cardId, UUID actorId) {
        Card card = cards.findByIdForUpdate(cardId).orElse(null);
        Resource locked = resources.findByIdForUpdate(resource.getId()).orElse(null);
        if (locked == null) throw new com.lumira.backend.common.error.ResourceNotFoundException("Resource not found");
        locked = resources.saveAndFlush(resource);
        if (card == null || !card.isShared()) return;
        var membership = memberships.findFirstByCardIdAndUserIdAndStatusIn(cardId, actorId,
                List.of(MembershipStatus.ACTIVE)).orElse(null);
        boolean canStillPublish = card.isOwnedBy(actorId)
                || (membership != null && membership.getRole() == MembershipRole.ADMIN);
        if (!canStillPublish) return;

        ResourceShare share = shares.findByResourceIdAndCardIdForUpdate(locked.getId(), cardId).orElse(null);
        if (share == null) shares.save(new ResourceShare(locked.getId(), cardId));
        else if (!share.isActive()) {
            share.activate();
            shares.save(share);
        }
        eventService.emit(cardId, "RESOURCE_ADDED", actorId,
                Map.of("resourceId", locked.getId(), "title", locked.getTitle(),
                        "status", locked.getStatus().name()));
    }
}
