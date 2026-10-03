package com.lumira.backend.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardMembershipRepository;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.card.CourseSpaceEvent;
import com.lumira.backend.card.CourseSpaceEventRepository;
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
    private final CourseSpaceEventRepository events;
    private final ObjectMapper mapper;

    public ResourcePublicationService(ResourceRepository resources, ResourceShareRepository shares,
            CardRepository cards, CardMembershipRepository memberships, CourseSpaceEventRepository events,
            ObjectMapper mapper) {
        this.resources = resources;
        this.shares = shares;
        this.cards = cards;
        this.memberships = memberships;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional
    public void completeUpload(Resource resource, UUID cardId, UUID actorId) {
        resources.saveAndFlush(resource);
        Card card = cards.findById(cardId).orElse(null);
        if (card == null || !card.isShared()) return;
        var membership = memberships.findFirstByCardIdAndUserIdAndStatusIn(cardId, actorId,
                List.of(MembershipStatus.ACTIVE)).orElse(null);
        boolean canStillPublish = card.isOwnedBy(actorId)
                || (membership != null && membership.getRole() == MembershipRole.ADMIN);
        if (!canStillPublish) return;

        ResourceShare share = shares.findByResourceIdAndCardId(resource.getId(), cardId).orElse(null);
        if (share == null) shares.save(new ResourceShare(resource.getId(), cardId));
        else if (!share.isActive()) {
            share.activate();
            shares.save(share);
        }
        events.save(new CourseSpaceEvent(cardId, "RESOURCE_ADDED", actorId,
                mapper.valueToTree(Map.of("resourceId", resource.getId(), "title", resource.getTitle(),
                        "status", resource.getStatus().name()))));
    }
}
