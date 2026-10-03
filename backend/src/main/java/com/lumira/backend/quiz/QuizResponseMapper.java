package com.lumira.backend.quiz;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class QuizResponseMapper {
    private final QuizQuestionRepository questions;
    private final QuizQuestionOptionRepository options;

    public QuizResponseMapper(QuizQuestionRepository questions, QuizQuestionOptionRepository options) {
        this.questions = questions;
        this.options = options;
    }

    public QuizResponse from(Quiz quiz, boolean revealCorrectness) {
        List<QuizQuestion> quizQuestions = questions.findByQuizIdOrderByPositionAsc(quiz.getId());
        Map<UUID, List<QuizQuestionOption>> byQuestion = options.findByQuestionIdInOrderByQuestionIdAscPositionAsc(
                        quizQuestions.stream().map(QuizQuestion::getId).toList())
                .stream().collect(Collectors.groupingBy(QuizQuestionOption::getQuestionId));
        List<QuizQuestionResponse> questionResponses = quizQuestions.stream()
                .map(question -> new QuizQuestionResponse(question.getId(), question.getPosition(), question.getPrompt(),
                        byQuestion.getOrDefault(question.getId(), List.of()).stream()
                                .map(option -> QuizOptionResponse.from(option, revealCorrectness)).toList()))
                .toList();
        return new QuizResponse(quiz.getId(), quiz.getOwner().getOwningCardId(), quiz.getOwner().getOwningUserId(),
                quiz.getTitle(), quiz.getDescription(), questionResponses, quiz.getCreatedAt(), quiz.getUpdatedAt());
    }
}
