package com.lumira.backend.quiz;

import java.util.List;
import java.util.UUID;

public record QuizQuestionResponse(UUID id, int position, String prompt, List<QuizOptionResponse> options) { }
