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

    /** AD-022/084: Resources default to shared when their Card becomes a Course Space. */
    public void activateResourcesForCard(UUID cardId) {
        for (Resource resource : resources.findByOwnerCardIdForUpdateOrderById(cardId)) {
            activate(ArtifactType.RESOURCE, resource.getId(), cardId);
        }
    }

    /** AD-063: dissolution revokes Course-Space visibility while retaining Resources. */
    public void deactivateAllForCard(UUID cardId) {
        for (ResourceShare snapshot : shares.findByCardIdAndActiveTrueOrderByCreatedAtAsc(cardId)) {
            if (snapshot.getResourceId() != null) {
                resources.findByIdForUpdate(snapshot.getResourceId()).ifPresent(resource ->
                        shares.findByResourceIdAndCardIdForUpdate(resource.getId(), cardId)
                                .filter(ResourceShare::isActive).ifPresent(ResourceShare::deactivate));
            } else if (snapshot.getNoteId() != null) {
                shares.findNoteShareForUpdate(snapshot.getNoteId(), cardId)
                        .filter(ResourceShare::isActive).ifPresent(ResourceShare::deactivate);
            } else if (snapshot.getStudySetId() != null) {
                shares.findStudySetShareForUpdate(snapshot.getStudySetId(), cardId)
                        .filter(ResourceShare::isActive).ifPresent(ResourceShare::deactivate);
            } else if (snapshot.getQuizId() != null) {
                shares.findQuizShareForUpdate(snapshot.getQuizId(), cardId)
                        .filter(ResourceShare::isActive).ifPresent(ResourceShare::deactivate);
            } else {
                shares.findFlashcardSetShareForUpdate(snapshot.getFlashcardSetId(), cardId)
                        .filter(ResourceShare::isActive).ifPresent(ResourceShare::deactivate);
            }
        }
    }

    private void activate(ArtifactType type, UUID artifactId, UUID cardId) {
        ResourceShare existing = switch (type) {
            case RESOURCE -> shares.findByResourceIdAndCardIdForUpdate(artifactId, cardId).orElse(null);
            case NOTE -> shares.findNoteShareForUpdate(artifactId, cardId).orElse(null);
            case STUDYSET -> shares.findStudySetShareForUpdate(artifactId, cardId).orElse(null);
            case QUIZ -> shares.findQuizShareForUpdate(artifactId, cardId).orElse(null);
            case FLASHCARD_SET -> shares.findFlashcardSetShareForUpdate(artifactId, cardId).orElse(null);
        };
        if (existing == null) shares.save(new ResourceShare(type, artifactId, cardId));
        else existing.activate();
    }
}
