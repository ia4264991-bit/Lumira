package com.lumira.backend.sarah;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardMembershipRepository;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.card.MembershipStatus;
import com.lumira.backend.flashcard.FlashcardRepository;
import com.lumira.backend.flashcard.FlashcardSet;
import com.lumira.backend.flashcard.FlashcardSetRepository;
import com.lumira.backend.quiz.Quiz;
import com.lumira.backend.quiz.QuizQuestionOptionRepository;
import com.lumira.backend.quiz.QuizQuestionRepository;
import com.lumira.backend.quiz.QuizRepository;
import com.lumira.backend.resource.ArtifactType;
import com.lumira.backend.resource.Resource;
import com.lumira.backend.resource.ResourceAuthorizationService;
import com.lumira.backend.resource.ResourceRepository;
import com.lumira.backend.resource.ResourceShare;
import com.lumira.backend.resource.ResourceShareRepository;
import com.lumira.backend.study.Note;
import com.lumira.backend.study.NoteRepository;
import com.lumira.backend.study.StudySet;
import com.lumira.backend.study.StudySetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builds a request-local context only after and while rechecking live Card authorization (AD-028/054/056/058). */
@Service
public class SarahContextBuilder {
    private final ResourceAuthorizationService authorization;
    private final CardMembershipRepository memberships;
    private final CardRepository cards;
    private final ResourceRepository resources;
    private final ResourceShareRepository shares;
    private final NoteRepository notes;
    private final StudySetRepository studySets;
    private final QuizRepository quizzes;
    private final QuizQuestionRepository questions;
    private final QuizQuestionOptionRepository options;
    private final FlashcardSetRepository flashcardSets;
    private final FlashcardRepository flashcards;
    private final ObjectMapper mapper;

    public SarahContextBuilder(ResourceAuthorizationService authorization, CardMembershipRepository memberships,
            CardRepository cards,
            ResourceRepository resources, ResourceShareRepository shares, NoteRepository notes,
            StudySetRepository studySets, QuizRepository quizzes, QuizQuestionRepository questions,
            QuizQuestionOptionRepository options, FlashcardSetRepository flashcardSets,
            FlashcardRepository flashcards, ObjectMapper mapper) {
        this.authorization = authorization;
        this.memberships = memberships;
        this.cards = cards;
        this.resources = resources;
        this.shares = shares;
        this.notes = notes;
        this.studySets = studySets;
        this.quizzes = quizzes;
        this.questions = questions;
        this.options = options;
        this.flashcardSets = flashcardSets;
        this.flashcards = flashcards;
        this.mapper = mapper;
    }

    @Transactional
    public List<SarahContextItem> build(UUID cardId, UUID userId) {
        Card target = lockAndAuthorizeCard(cardId, userId);
        UUID personalContextCardId = cardId;
        if (target.isShared() && !target.isOwnedBy(userId)) {
            personalContextCardId = memberships.findCurrentForAuthorizationReadLock(cardId, userId,
                            List.of(MembershipStatus.ACTIVE))
                    .orElseThrow(() -> new com.lumira.backend.common.error.ResourceNotFoundException("Course Space not found"))
                    .getMemberCardId();
        }
        List<SarahContextItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        addPrivateOwnedContent(cardId, userId, personalContextCardId, result, seen);
        if (target.isShared()) {
            lockAndAuthorizeCard(cardId, userId);
            for (ResourceShare share : shares.findByCardIdAndActiveTrue(cardId)) {
                lockAndAuthorizeCard(cardId, userId);
                addSharedArtifact(share, cardId, userId, result, seen);
            }
        }
        return List.copyOf(result);
    }

    @Transactional
    public void requireContextualResource(UUID cardId, UUID resourceId, UUID userId) {
        Card target = lockAndAuthorizeCard(cardId, userId);
        Resource resource = resources.findById(resourceId)
                .orElseThrow(() -> new com.lumira.backend.common.error.ResourceNotFoundException("Resource not found"));
        boolean ownedByUser = resource.getOwner().isUserOwned()
                ? resource.getOwner().getOwningUserId().equals(userId)
                : resourceBelongsToPersonalContext(resource.getOwner().getOwningCardId(), cardId, target, userId);
        boolean sharedIntoTarget = target.isShared()
                && shares.findActiveForArtifactCardForRead("resource", resourceId, cardId).isPresent();
        if (!ownedByUser && !sharedIntoTarget) {
            throw new com.lumira.backend.common.error.ResourceNotFoundException("Resource not found");
        }
        if (resource.getStatus() != com.lumira.backend.resource.ResourceStatus.READY) {
            throw new com.lumira.backend.common.error.ConflictException("Resource is not ready for Sarah context");
        }
    }

    @Transactional
    public List<SarahContextItem> buildForResources(UUID cardId, List<UUID> resourceIds, UUID userId) {
        lockAndAuthorizeCard(cardId, userId);
        List<SarahContextItem> result = new ArrayList<>();
        for (UUID id : resourceIds) {
            requireContextualResource(cardId, id, userId);
            Resource resource = resources.findById(id).orElseThrow();
            result.add(new SarahContextItem("resource", id, resource.getTitle(), resource.getExtractedContent().toString()));
        }
        return List.copyOf(result);
    }

    private boolean resourceBelongsToPersonalContext(UUID ownerCardId, UUID cardId, Card target, UUID userId) {
        if (!target.isShared()) return ownerCardId.equals(cardId) && target.isOwnedBy(userId);
        if (target.isOwnedBy(userId)) return ownerCardId.equals(cardId);
        return memberships.findCurrentForAuthorizationReadLock(cardId, userId, List.of(MembershipStatus.ACTIVE))
                .map(membership -> membership.getMemberCardId().equals(ownerCardId)).orElse(false);
    }

    private void addPrivateOwnedContent(UUID cardId, UUID userId, UUID ownCardId,
            List<SarahContextItem> result, Set<String> seen) {
        authorization.requireCardReadable(cardId, userId);
        for (Resource resource : resources.findByOwner_OwningUserIdOrderByCreatedAtDesc(userId)) {
            if (resource.getStatus() == com.lumira.backend.resource.ResourceStatus.READY) {
                add(result, seen, "resource", resource.getId(), resource.getTitle(), resource.getExtractedContent().toString());
            }
        }
        authorization.requireCardReadable(cardId, userId);
        for (Note note : notes.findByOwner_OwningUserIdOrderByCreatedAtDesc(userId)) {
            add(result, seen, "note", note.getId(), note.getTitle(), note.getContent());
        }
        authorization.requireCardReadable(cardId, userId);
        for (StudySet set : studySets.findByOwner_OwningUserIdOrderByCreatedAtDesc(userId)) {
            add(result, seen, "studyset", set.getId(), set.getTitle(), set.getDescription());
        }
        authorization.requireCardReadable(cardId, userId);
        for (Quiz quiz : quizzes.findByOwner_OwningUserIdOrderByCreatedAtDesc(userId)) {
            addQuiz(quiz, cardId, userId, result, seen);
        }
        authorization.requireCardReadable(cardId, userId);
        for (FlashcardSet set : flashcardSets.findByOwner_OwningUserIdOrderByCreatedAtDesc(userId)) {
            addFlashcardSet(set, cardId, userId, result, seen);
        }
        authorization.requireCardReadable(cardId, userId);
        for (Resource resource : resources.findByOwner_OwningCardIdOrderByCreatedAtDesc(ownCardId)) {
            if (resource.getStatus() == com.lumira.backend.resource.ResourceStatus.READY) {
                add(result, seen, "resource", resource.getId(), resource.getTitle(), resource.getExtractedContent().toString());
            }
        }
        authorization.requireCardReadable(cardId, userId);
        for (Note note : notes.findByOwner_OwningCardIdOrderByCreatedAtDesc(ownCardId)) {
            add(result, seen, "note", note.getId(), note.getTitle(), note.getContent());
        }
        authorization.requireCardReadable(cardId, userId);
        for (StudySet set : studySets.findByOwner_OwningCardIdOrderByCreatedAtDesc(ownCardId)) {
            add(result, seen, "studyset", set.getId(), set.getTitle(), set.getDescription());
        }
        authorization.requireCardReadable(cardId, userId);
        for (Quiz quiz : quizzes.findByOwner_OwningCardIdOrderByCreatedAtDesc(ownCardId)) {
            addQuiz(quiz, cardId, userId, result, seen);
        }
        authorization.requireCardReadable(cardId, userId);
        for (FlashcardSet set : flashcardSets.findByOwner_OwningCardIdOrderByCreatedAtDesc(ownCardId)) {
            addFlashcardSet(set, cardId, userId, result, seen);
        }
    }

    private void addSharedArtifact(ResourceShare share, UUID cardId, UUID userId,
            List<SarahContextItem> result, Set<String> seen) {
        ArtifactType type = share.getArtifactType();
        UUID artifactId = share.getArtifactId();
        authorization.requireCardReadable(cardId, userId);
        ResourceShare liveShare = shares.findActiveForArtifactCardForRead(type.apiValue(), artifactId, cardId)
                .filter(ResourceShare::isActive).orElse(null);
        if (liveShare == null) return;
        switch (type) {
            case RESOURCE -> resources.findById(artifactId).filter(r -> r.getStatus() == com.lumira.backend.resource.ResourceStatus.READY)
                    .ifPresent(r -> add(result, seen, "resource", r.getId(), r.getTitle(), r.getExtractedContent().toString()));
            case NOTE -> notes.findById(artifactId).ifPresent(n -> add(result, seen, "note", n.getId(), n.getTitle(), n.getContent()));
            case STUDYSET -> studySets.findById(artifactId).ifPresent(s -> add(result, seen, "studyset", s.getId(), s.getTitle(), s.getDescription()));
            case QUIZ -> quizzes.findById(artifactId).ifPresent(q -> addQuiz(q, cardId, userId, result, seen));
            case FLASHCARD_SET -> flashcardSets.findById(artifactId).ifPresent(s -> addFlashcardSet(s, cardId, userId, result, seen));
        }
    }

    private void addQuiz(Quiz quiz, UUID cardId, UUID userId, List<SarahContextItem> result, Set<String> seen) {
        authorization.requireCardReadable(cardId, userId);
        var quizQuestions = questions.findByQuizIdOrderByPositionAsc(quiz.getId());
        List<Object> questionContent = new ArrayList<>();
        for (var question : quizQuestions) {
            authorization.requireCardReadable(cardId, userId);
            var questionOptions = options.findByQuestionIdOrderByPositionAsc(question.getId());
            questionContent.add(java.util.Map.of("prompt", question.getPrompt(), "options", questionOptions.stream()
                    .map(o -> java.util.Map.of("text", o.getText(), "correct", o.isCorrect())).toList()));
        }
        add(result, seen, "quiz", quiz.getId(), quiz.getTitle(), json(questionContent));
    }

    private void addFlashcardSet(FlashcardSet set, UUID cardId, UUID userId,
            List<SarahContextItem> result, Set<String> seen) {
        authorization.requireCardReadable(cardId, userId);
        var cardContent = flashcards.findByFlashcardSetIdOrderByPositionAsc(set.getId()).stream()
                .map(f -> java.util.Map.of("front", f.getFront(), "back", f.getBack())).toList();
        add(result, seen, "flashcard_set", set.getId(), set.getTitle(), json(cardContent));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize authorized Sarah context", ex); }
    }

    private void add(List<SarahContextItem> result, Set<String> seen, String type, UUID id, String title, String content) {
        if (seen.add(type + ":" + id)) result.add(new SarahContextItem(type, id, title, content));
    }

    private Card lockAndAuthorizeCard(UUID cardId, UUID userId) {
        authorization.requireCardReadable(cardId, userId);
        Card card = cards.findByIdForAuthorizationReadLock(cardId)
                .orElseThrow(() -> new com.lumira.backend.common.error.ResourceNotFoundException("Card not found"));
        if (card.isOwnedBy(userId)) return card;
        if (!card.isShared() || memberships.findCurrentForAuthorizationReadLock(cardId, userId,
                List.of(MembershipStatus.ACTIVE)).isEmpty()) {
            throw new com.lumira.backend.common.error.ResourceNotFoundException("Card not found");
        }
        return card;
    }
}
