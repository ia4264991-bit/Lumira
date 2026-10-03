package com.lumira.backend.resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ResourceShareService {
    private final ResourceRepository resources;
    private final ResourceShareRepository shares;

    public ResourceShareService(ResourceRepository resources, ResourceShareRepository shares) {
        this.resources = resources;
        this.shares = shares;
    }

    /** AD-022: resources become shared when the owning Card becomes a Course Space. */
    public void activateAllForCard(UUID cardId) {
        for (Resource resource : resources.findByOwnerCardIdForUpdateOrderById(cardId)) {
            ResourceShare share = shares.findByResourceIdAndCardIdForUpdate(resource.getId(), cardId).orElse(null);
            if (share == null) shares.save(new ResourceShare(resource.getId(), cardId));
            else share.activate();
        }
    }

    /** AD-063: dissolution revokes Course-Space visibility while retaining Resources. */
    public void deactivateAllForCard(UUID cardId) {
        for (ResourceShare snapshot : shares.findByCardIdAndActiveTrueOrderByResourceIdAsc(cardId)) {
            resources.findByIdForUpdate(snapshot.getResourceId()).ifPresent(resource ->
                    shares.findByResourceIdAndCardIdForUpdate(resource.getId(), cardId)
                            .filter(ResourceShare::isActive).ifPresent(ResourceShare::deactivate));
        }
    }
}
