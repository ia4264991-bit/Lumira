package com.lumira.backend.flashcard;

import jakarta.validation.Valid;

import java.util.List;

public record FlashcardSetPatchRequest(String title, String description,
        List<@Valid FlashcardInput> cards) { }
