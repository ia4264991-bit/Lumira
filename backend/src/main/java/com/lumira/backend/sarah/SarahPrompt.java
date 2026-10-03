package com.lumira.backend.sarah;

import java.util.List;
import java.util.UUID;

/** Authorized, request-scoped input passed to the server-side AI Router. */
public record SarahPrompt(
        UUID cardId,
        String question,
        String selectedText,
        Integer pageIndex,
        List<ConversationTurn> conversationHistory,
        List<SarahContextItem> authorizedContext) { }
