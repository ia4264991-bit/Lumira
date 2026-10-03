package com.lumira.backend.sarah;

import com.lumira.backend.common.error.AiProviderUnavailableException;
import com.lumira.backend.resource.ResourceAuthorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.backend.common.error.ErrorCode;
import com.lumira.backend.common.error.ValidationException;
import com.lumira.backend.flashcard.FlashcardInput;
import com.lumira.backend.flashcard.FlashcardSetCreateRequest;
import com.lumira.backend.flashcard.FlashcardSetService;
import com.lumira.backend.quiz.QuizCreateRequest;
import com.lumira.backend.quiz.QuizService;
import com.lumira.backend.study.StudySetCreateRequest;
import com.lumira.backend.study.StudySetService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

@Service
public class SarahService {
    private final ResourceAuthorizationService authorization;
    private final SarahContextBuilder contexts;
    private final SarahUsageService usage;
    private final ObjectProvider<AiRouter> routers;
    private final ObjectMapper mapper;
    private final FlashcardSetService flashcardSets;
    private final QuizService quizzes;
    private final StudySetService studySets;

    public SarahService(ResourceAuthorizationService authorization, SarahContextBuilder contexts,
            SarahUsageService usage, ObjectProvider<AiRouter> routers, ObjectMapper mapper,
            FlashcardSetService flashcardSets, QuizService quizzes, StudySetService studySets) {
        this.authorization = authorization;
        this.contexts = contexts;
        this.usage = usage;
        this.routers = routers;
        this.mapper = mapper;
        this.flashcardSets = flashcardSets;
        this.quizzes = quizzes;
        this.studySets = studySets;
    }

    public SarahAskResponse askWorkspace(UUID cardId, UUID userId, WorkspaceAskRequest request) {
        authorization.requireCardReadable(cardId, userId);
        AiRouter router = requireRouter();
        List<SarahContextItem> authorizedContext = contexts.build(cardId, userId);
        UsageSnapshot snapshot = usage.reserveRequest(userId);
        String answer = router.answer(new SarahPrompt(cardId, request.question(), null, null,
                safeHistory(request.conversationHistory()), authorizedContext));
        return response(answer, request.conversationId(), snapshot);
    }

    public SarahAskResponse askContextual(UUID resourceId, UUID userId, ContextualAskRequest request) {
        authorization.requireCardReadable(request.cardId(), userId);
        contexts.requireContextualResource(request.cardId(), resourceId, userId);
        AiRouter router = requireRouter();
        List<SarahContextItem> authorizedContext = contexts.build(request.cardId(), userId);
        // Revalidate the selected Resource after workspace retrieval and immediately before it enters the prompt.
        contexts.requireContextualResource(request.cardId(), resourceId, userId);
        UsageSnapshot snapshot = usage.reserveRequest(userId);
        String answer = router.answer(new SarahPrompt(request.cardId(), request.question(), request.selectedText(),
                request.pageIndex(), safeHistory(request.conversationHistory()), authorizedContext));
        return response(answer, request.conversationId(), snapshot);
    }

    public SarahGeneratedArtifact generate(UUID cardId, UUID userId, SarahGenerationRequest request) {
        authorization.requireCardUpload(cardId, userId);
        if (request.sourceResourceIds().stream().distinct().count() != request.sourceResourceIds().size()) {
            throw generationFailure("Source Resource IDs must be unique");
        }
        List<SarahContextItem> sources = contexts.buildForResources(cardId, request.sourceResourceIds(), userId);
        AiRouter router = requireRouter();
        UsageSnapshot usageSnapshot = usage.reserveRequest(userId);
        String output = router.generate(new SarahGenerationPrompt(cardId, request.artifactType().name(),
                request.instructions(), sources));
        try {
            JsonNode node = mapper.readTree(output);
            validateGeneratedShape(request.artifactType(), node);
            Object artifact = switch (request.artifactType()) {
                case FLASHCARDSET -> flashcardSets.create(cardId, userId, mapper.treeToValue(node, FlashcardSetCreateRequest.class));
                case QUIZ -> quizzes.create(cardId, userId, mapper.treeToValue(node, QuizCreateRequest.class));
                case STUDYSET -> studySets.create(cardId, userId, mapper.treeToValue(node, StudySetCreateRequest.class));
            };
            return new SarahGeneratedArtifact(request.artifactType(), artifact,
                    new SarahGeneratedArtifact.Provenance("SARAH", request.sourceResourceIds().stream()
                            .map(id -> new SarahGeneratedArtifact.Source("resource", id)).toList()),
                    usageSnapshot.used(), usageSnapshot.limit());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) {
            throw generationFailure("Sarah output did not match the artifact schema");
        } catch (ValidationException ex) {
            throw generationFailure("Sarah output failed artifact validation");
        }
    }

    private ValidationException generationFailure(String message) {
        return new ValidationException(ErrorCode.GENERATION_VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                message, List.of());
    }

    private void validateGeneratedShape(SarahGenerationType type, JsonNode node) {
        if (node == null || !node.isObject()) throw generationFailure("Sarah output must be a JSON object");
        switch (type) {
            case FLASHCARDSET -> {
                requireKeys(node, Set.of("title", "description", "cards"));
                requireText(node, "title"); requireText(node, "description");
                JsonNode cards = node.get("cards");
                if (cards == null || !cards.isArray() || cards.isEmpty()) throw generationFailure("FlashcardSet needs cards");
                Set<Integer> positions = new HashSet<>();
                for (JsonNode card : cards) {
                    requireKeys(card, Set.of("position", "front", "back"));
                    if (!card.path("position").canConvertToInt() || card.path("position").asInt() < 1 ||
                            !positions.add(card.path("position").asInt())) throw generationFailure("Invalid flashcard position");
                    requireText(card, "front"); requireText(card, "back");
                }
            }
            case QUIZ -> {
                requireKeys(node, Set.of("title", "description", "questions"));
                requireText(node, "title"); requireText(node, "description");
                JsonNode questions = node.get("questions");
                if (questions == null || !questions.isArray() || questions.isEmpty()) throw generationFailure("Quiz needs questions");
                Set<Integer> questionPositions = new HashSet<>();
                for (JsonNode question : questions) {
                    requireKeys(question, Set.of("position", "prompt", "options"));
                    requireText(question, "prompt");
                    int pos = question.path("position").asInt(-1);
                    if (pos < 1 || !questionPositions.add(pos)) throw generationFailure("Invalid question position");
                    JsonNode options = question.get("options");
                    if (options == null || !options.isArray() || options.size() < 2) throw generationFailure("Question needs at least two options");
                    Set<Integer> optionPositions = new HashSet<>(); int correct = 0;
                    for (JsonNode option : options) {
                        requireKeys(option, Set.of("position", "text", "correct"));
                        requireText(option, "text");
                        int optionPos = option.path("position").asInt(-1);
                        if (optionPos < 1 || !optionPositions.add(optionPos) || !option.path("correct").isBoolean())
                            throw generationFailure("Invalid option");
                        if (option.path("correct").asBoolean()) correct++;
                    }
                    if (correct != 1) throw generationFailure("Each question must have exactly one correct option");
                }
            }
            case STUDYSET -> { requireKeys(node, Set.of("title", "description")); requireText(node, "title"); requireText(node, "description"); }
        }
    }

    private void requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw generationFailure("Invalid generated field: " + field);
    }

    private void requireKeys(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw generationFailure("Invalid generated object");
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw generationFailure("Generated output contains missing or unapproved fields");
    }

    private AiRouter requireRouter() {
        AiRouter router = routers.getIfAvailable();
        if (router == null) throw new AiProviderUnavailableException("No server-side Sarah AI provider is configured");
        return router;
    }

    private List<ConversationTurn> safeHistory(List<ConversationTurn> history) {
        return history == null ? List.of() : List.copyOf(history);
    }

    private SarahAskResponse response(String answer, UUID conversationId, UsageSnapshot snapshot) {
        if (answer == null || answer.isBlank()) throw new AiProviderUnavailableException("Sarah's AI provider returned no answer");
        return new SarahAskResponse(answer, conversationId, snapshot.used(), snapshot.limit());
    }
}
