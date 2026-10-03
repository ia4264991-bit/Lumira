package com.lumira.backend.quiz;

import jakarta.validation.Valid;

import java.util.List;

public record QuizPatchRequest(String title, String description,
        List<@Valid QuizQuestionInput> questions) { }
