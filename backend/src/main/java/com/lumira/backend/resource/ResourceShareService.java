package com.lumira.backend.resource;

import com.lumira.backend.study.Note;
import com.lumira.backend.study.NoteRepository;
import com.lumira.backend.study.StudySet;
import com.lumira.backend.study.StudySetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ResourceShareService {
    private final ResourceRepository resources;
    private final ResourceShareRepository shares;
    private final NoteRepository notes;
    private final StudySetRepository studySets;

    public ResourceShareService(ResourceRepository resources, ResourceShareRepository shares,
            NoteRepository notes, StudySetRepository studySets) {
        this.resources = resources;
        this.shares = shares;
        this.notes = notes;
        this.studySets = studySets;
    }

    /** AD-022: resources become shared when the owning Card becomes a Course Space. */
    public void activateAllForCard(UUID cardId) {
        for (Resource resource : resources.findByOwnerCardIdForUpdateOrderById(cardId)) {
            activate(ArtifactType.RESOURCE, resource.getId(), cardId);
        }
        for (Note note : notes.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId)) activate(ArtifactType.NOTE, note.getId(), cardId);
        for (StudySet set : studySets.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId)) activate(ArtifactType.STUDYSET, set.getId(), cardId);
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
            } else {
                shares.findStudySetShareForUpdate(snapshot.getStudySetId(), cardId)
                        .filter(ResourceShare::isActive).ifPresent(ResourceShare::deactivate);
            }
        }
    }

    private void activate(ArtifactType type, UUID artifactId, UUID cardId) {
        ResourceShare existing = switch (type) {
            case RESOURCE -> shares.findByResourceIdAndCardIdForUpdate(artifactId, cardId).orElse(null);
            case NOTE -> shares.findNoteShareForUpdate(artifactId, cardId).orElse(null);
            case STUDYSET -> shares.findStudySetShareForUpdate(artifactId, cardId).orElse(null);
        };
        if (existing == null) shares.save(new ResourceShare(type, artifactId, cardId));
        else existing.activate();
    }
}
