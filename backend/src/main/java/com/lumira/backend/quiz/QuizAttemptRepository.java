package com.lumira.backend.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    List<QuizAttempt> findByQuizIdAndUserIdOrderBySubmittedAtDesc(UUID quizId, UUID userId);
    void deleteByUserId(UUID userId);
}
