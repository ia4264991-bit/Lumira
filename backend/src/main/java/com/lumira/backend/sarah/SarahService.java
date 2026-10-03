package com.lumira.backend.sarah;

import com.lumira.backend.common.error.AiProviderUnavailableException;
import com.lumira.backend.resource.ResourceAuthorizationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SarahService {
    private final ResourceAuthorizationService authorization;
    private final SarahContextBuilder contexts;
    private final SarahUsageService usage;
    private final ObjectProvider<AiRouter> routers;

    public SarahService(ResourceAuthorizationService authorization, SarahContextBuilder contexts,
            SarahUsageService usage, ObjectProvider<AiRouter> routers) {
        this.authorization = authorization;
        this.contexts = contexts;
        this.usage = usage;
        this.routers = routers;
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
