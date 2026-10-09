package com.lumira.backend.study;

import com.lumira.backend.card.Card;
import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.common.error.ValidationException;
import com.lumira.backend.resource.ArtifactType;
import com.lumira.backend.resource.ResourceAuthorizationService;
import com.lumira.backend.resource.ResourceShare;
import com.lumira.backend.resource.ResourceShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class StudySetService {
    private final StudySetRepository studySets;
    private final ResourceShareRepository shares;
    private final ResourceAuthorizationService authorization;
    public StudySetService(StudySetRepository studySets, ResourceShareRepository shares,
            ResourceAuthorizationService authorization) {
        this.studySets = studySets;
        this.shares = shares;
        this.authorization = authorization;
    }

    @Transactional
    public StudySetResponse create(UUID cardId, UUID actorId, StudySetCreateRequest request) {
        authorization.requirePrivateArtifactCardForCreate(cardId, actorId);
        StudySet set = studySets.saveAndFlush(new StudySet(ArtifactOwner.forCard(cardId), request.title(), request.description()));
        return StudySetResponse.from(set);
    }

    @Transactional(readOnly = true)
    public List<StudySetResponse> list(UUID cardId, UUID actorId) {
        Card card = authorization.requireCardReadable(cardId, actorId);
        if (!card.isShared()) return studySets.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId).stream().map(StudySetResponse::from).toList();
        List<UUID> sharedIds = shares.findByCardIdAndActiveTrueOrderByCreatedAtAsc(cardId).stream()
                .filter(s -> s.getStudySetId() != null).map(ResourceShare::getStudySetId).toList();
        var visible = new LinkedHashMap<UUID, StudySet>();
        if (card.isOwnedBy(actorId)) {
            studySets.findByOwner_OwningCardIdOrderByCreatedAtDesc(cardId).forEach(set -> visible.put(set.getId(), set));
        }
        studySets.findAllById(sharedIds).forEach(set -> visible.put(set.getId(), set));
        return visible.values().stream().sorted(Comparator.comparing(StudySet::getCreatedAt).reversed())
                .map(set -> StudySetResponse.from(set, sharedIds.contains(set.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public StudySetResponse get(UUID id, UUID actorId) {
        StudySet set = studySets.findById(id).orElseThrow(() -> notFound());
        authorization.requireArtifactReadable(set.getOwner(), ArtifactType.STUDYSET, id, "Study Set not found", actorId);
        return StudySetResponse.from(set);
    }

    @Transactional
    public StudySetResponse update(UUID id, UUID actorId, StudySetPatchRequest request) {
        StudySet set = studySets.findByIdForUpdate(id).orElseThrow(() -> notFound());
        authorization.requireArtifactOwner(set.getOwner(), "Study Set not found", actorId);
        if (request.title() != null && request.title().isBlank()) throw new ValidationException("title must not be blank");
        set.update(request.title(), request.description());
        studySets.saveAndFlush(set);
        return StudySetResponse.from(set);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        StudySet set = studySets.findByIdForUpdate(id).orElseThrow(() -> notFound());
        authorization.requireArtifactOwner(set.getOwner(), "Study Set not found", actorId);
        studySets.delete(set);
    }

    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Study Set not found"); }
}
