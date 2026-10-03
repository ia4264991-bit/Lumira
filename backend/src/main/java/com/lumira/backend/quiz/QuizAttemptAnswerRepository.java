package com.lumira.backend.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, UUID> {
    List<QuizAttemptAnswer> findByAttemptIdOrderByQuestionPositionAsc(UUID attemptId);
}
