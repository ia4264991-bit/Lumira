package com.lumira.backend.quiz;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Collection;
import java.util.function.Predicate;
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
        return fromMany(List.of(quiz), ignored -> revealCorrectness).getFirst();
    }

    public List<QuizResponse> fromMany(List<Quiz> quizzes, Predicate<Quiz> revealCorrectness) {
        return fromMany(quizzes, revealCorrectness, Set.of());
    }

    public List<QuizResponse> fromMany(List<Quiz> quizzes, Predicate<Quiz> revealCorrectness, Set<UUID> sharedIds) {
        if (quizzes.isEmpty()) return List.of();
        List<UUID> quizIds = quizzes.stream().map(Quiz::getId).toList();
        Map<UUID, List<QuizQuestion>> byQuiz = questions.findByQuizIdInOrderByQuizIdAscPositionAsc(quizIds).stream()
                .collect(Collectors.groupingBy(QuizQuestion::getQuizId));
        List<QuizQuestion> allQuestions = byQuiz.values().stream().flatMap(Collection::stream).toList();
        Map<UUID, List<QuizQuestionOption>> byQuestion = options.findByQuestionIdInOrderByQuestionIdAscPositionAsc(
                        allQuestions.stream().map(QuizQuestion::getId).toList())
                .stream().collect(Collectors.groupingBy(QuizQuestionOption::getQuestionId));
        return quizzes.stream().map(quiz -> {
            boolean reveal = revealCorrectness.test(quiz);
            List<QuizQuestionResponse> questionResponses = byQuiz.getOrDefault(quiz.getId(), List.of()).stream()
                    .map(question -> new QuizQuestionResponse(question.getId(), question.getPosition(), question.getPrompt(),
                            byQuestion.getOrDefault(question.getId(), List.of()).stream()
                                    .map(option -> QuizOptionResponse.from(option, reveal)).toList()))
                    .toList();
            return new QuizResponse(quiz.getId(), quiz.getOwner().getOwningCardId(), quiz.getOwner().getOwningUserId(),
                    quiz.getTitle(), quiz.getDescription(), questionResponses, quiz.getCreatedAt(), quiz.getUpdatedAt(),
                    sharedIds.contains(quiz.getId()));
        }).toList();
    }
}
