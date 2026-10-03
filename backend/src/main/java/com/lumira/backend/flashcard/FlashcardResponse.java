package com.lumira.backend.flashcard;

import java.util.UUID;

public record FlashcardResponse(UUID id, int position, String front, String back) { }
