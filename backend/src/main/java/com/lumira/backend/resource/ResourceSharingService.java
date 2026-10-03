package com.lumira.backend.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.card.CourseSpaceEventService;
import com.lumira.backend.card.MembershipRole;
import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.common.error.ValidationException;
import com.lumira.backend.study.Note;
import com.lumira.backend.study.NoteRepository;
import com.lumira.backend.study.StudySet;
import com.lumira.backend.study.StudySetRepository;
import com.lumira.backend.quiz.Quiz;
import com.lumira.backend.quiz.QuizRepository;
import com.lumira.backend.quiz.QuizResponseMapper;
import com.lumira.backend.flashcard.FlashcardSetRepository;
import com.lumira.backend.flashcard.FlashcardSetResponseMapper;
import com.lumira.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** Shared AD-045 lifecycle for every artifact type currently in B0-B7. */
@Service
@Transactional
public class ResourceSharingService {
    private static final Set<String> MODERATION_REASONS = Set.of(
            "COPYRIGHT", "PRIVACY", "SAFETY", "ABUSE", "MALICIOUS_CONTENT", "POLICY_VIOLATION", "OTHER");

    private final ResourceRepository resources;
    private final NoteRepository notes;
    private final StudySetRepository studySets;
    private final QuizRepository quizzes;
    private final QuizResponseMapper quizMapper;
    private final FlashcardSetRepository flashcardSets;
    private final FlashcardSetResponseMapper flashcardMapper;
    private final ResourceShareRepository shares;
    private final CardRepository cards;
    private final CourseSpaceEventService eventService;
    private final ResourceAuthorizationService authorization;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public ResourceSharingService(ResourceRepository resources, NoteRepository notes, StudySetRepository studySets,
            QuizRepository quizzes, QuizResponseMapper quizMapper, FlashcardSetRepository flashcardSets,
            FlashcardSetResponseMapper flashcardMapper,
            ResourceShareRepository shares, CardRepository cards, CourseSpaceEventService eventService,
            ResourceAuthorizationService authorization, UserRepository users, ObjectMapper mapper) {
        this.resources = resources;
        this.notes = notes;
        this.studySets = studySets;
        this.quizzes = quizzes;
        this.quizMapper = quizMapper;
        this.flashcardSets = flashcardSets;
        this.flashcardMapper = flashcardMapper;
        this.shares = shares;
        this.cards = cards;
        this.eventService = eventService;
        this.authorization = authorization;
        this.users = users;
        this.mapper = mapper;
    }

    public ShareResult share(ArtifactType type, UUID artifactId, UUID cardId, UUID actorId) {
        lockActor(actorId);
        Card card = lockCourseSpace(cardId);
        authorization.requireActiveMember(card, actorId);
        ArtifactOwner owner = lockArtifact(type, artifactId);
        authorization.requireArtifactOwner(owner, "Artifact not found", actorId);

        ResourceShare share = lockShare(type, artifactId, cardId).orElse(null);
        if (share != null && share.isActive()) return new ShareResult(share, false);
        if (share == null) share = shares.save(new ResourceShare(type, artifactId, cardId));
        else share.activate();
        emit(card, "ARTIFACT_SHARED", actorId, type, artifactId, owner, "SHARE", null, null);
        return new ShareResult(share, true);
    }

    public Object unshare(ArtifactType type, UUID artifactId, UUID cardId, UUID actorId) {
        lockActor(actorId);
        Card card = cards.findByIdForUpdate(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Space not found"));
        if (!card.isShared()) throw new ResourceNotFoundException("Course Space not found");
        ArtifactOwner owner = lockArtifact(type, artifactId);
        authorization.requireArtifactOwner(owner, "Artifact not found", actorId);
        ResourceShare share = lockShare(type, artifactId, cardId)
                .filter(ResourceShare::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active share not found"));
        share.deactivate();
        emit(card, "CONTENT_UNSHARED", actorId, type, artifactId, owner, "UNSHARE", null, null);
        return artifact(type, artifactId, actorId);
    }

    public Object forceUnshare(ArtifactType type, UUID artifactId, UUID cardId, UUID actorId,
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
        ArtifactOwner owner = lockArtifact(type, artifactId);
        ResourceShare share = lockShare(type, artifactId, cardId)
                .filter(ResourceShare::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active share not found"));
        share.deactivate();
        emit(card, "CONTENT_FORCE_UNSHARED", actorId, type, artifactId, owner, "FORCE_UNSHARE", reason, note);
        return artifact(type, artifactId, actorId);
    }

    public void createCardShareIfShared(ArtifactType type, UUID artifactId, UUID cardId, UUID actorId) {
        Card card = cards.findByIdForUpdate(cardId).orElse(null);
        if (card == null || !card.isShared()) return;
        ResourceShare share = lockShare(type, artifactId, cardId).orElse(null);
        if (share != null && share.isActive()) return;
        if (share == null) shares.save(new ResourceShare(type, artifactId, cardId));
        else share.activate();
        ArtifactOwner owner = lockArtifact(type, artifactId);
        emit(card, "ARTIFACT_SHARED", actorId, type, artifactId, owner, "SHARE", null, null);
    }

    private Card lockCourseSpace(UUID cardId) {
        Card card = cards.findByIdForUpdate(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Space not found"));
        if (!card.isShared()) throw new ResourceNotFoundException("Course Space not found");
        return card;
    }

    private void lockActor(UUID actorId) {
        if (users.findByIdForUpdate(actorId).isEmpty()) throw new ResourceNotFoundException("Account not found");
    }

    private ArtifactOwner lockArtifact(ArtifactType type, UUID id) {
        return switch (type) {
            case RESOURCE -> resources.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Artifact not found")).getOwner();
            case NOTE -> notes.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Artifact not found")).getOwner();
            case STUDYSET -> studySets.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Artifact not found")).getOwner();
            case QUIZ -> quizzes.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Artifact not found")).getOwner();
            case FLASHCARD_SET -> flashcardSets.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Artifact not found")).getOwner();
        };
    }

    private Object artifact(ArtifactType type, UUID id, UUID actorId) {
        return switch (type) {
            case RESOURCE -> resources.findById(id).map(ResourceResponse::from).orElseThrow(() -> new ResourceNotFoundException("Artifact not found"));
            case NOTE -> notes.findById(id).map(com.lumira.backend.study.NoteResponse::from).orElseThrow(() -> new ResourceNotFoundException("Artifact not found"));
            case STUDYSET -> studySets.findById(id).map(com.lumira.backend.study.StudySetResponse::from).orElseThrow(() -> new ResourceNotFoundException("Artifact not found"));
            case QUIZ -> quizzes.findById(id).map(quiz -> quizMapper.from(quiz, authorization.isOwner(quiz.getOwner(), actorId)))
                    .orElseThrow(() -> new ResourceNotFoundException("Artifact not found"));
            case FLASHCARD_SET -> flashcardSets.findById(id).map(flashcardMapper::from)
                    .orElseThrow(() -> new ResourceNotFoundException("Artifact not found"));
        };
    }

    private java.util.Optional<ResourceShare> lockShare(ArtifactType type, UUID artifactId, UUID cardId) {
        return switch (type) {
            case RESOURCE -> shares.findByResourceIdAndCardIdForUpdate(artifactId, cardId);
            case NOTE -> shares.findNoteShareForUpdate(artifactId, cardId);
            case STUDYSET -> shares.findStudySetShareForUpdate(artifactId, cardId);
            case QUIZ -> shares.findQuizShareForUpdate(artifactId, cardId);
            case FLASHCARD_SET -> shares.findFlashcardSetShareForUpdate(artifactId, cardId);
        };
    }

    private ObjectNode payload(ArtifactType type, UUID id, ArtifactOwner owner, String operation,
            String reason, String note) {
        ObjectNode payload = mapper.createObjectNode().put("artifactType", type.apiValue())
                .put("artifactId", id.toString()).put("operation", operation);
        if (type == ArtifactType.RESOURCE) payload.put("resourceId", id.toString());
        UUID ownerUserId = owner.isUserOwned() ? owner.getOwningUserId()
                : cards.findById(owner.getOwningCardId()).map(Card::getOwnerId).orElse(null);
        if (ownerUserId != null) payload.put("artifactOwnerUserId", ownerUserId.toString());
        if (operation.equals("FORCE_UNSHARE")) {
            if (reason == null) payload.putNull("reason"); else payload.put("reason", reason);
            if (note == null || note.isBlank()) payload.putNull("note"); else payload.put("note", note.strip());
        }
        return payload;
    }

    private void emit(Card card, String eventType, UUID actorId, ArtifactType type, UUID id,
            ArtifactOwner owner, String operation, String reason, String note) {
        eventService.emit(card.getId(), eventType, actorId,
                payload(type, id, owner, operation, reason, note));
    }

    public record ShareResult(ResourceShare share, boolean changed) { }
}
