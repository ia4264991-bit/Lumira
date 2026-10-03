package com.lumira.backend.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuizAttemptAnswerOptionRepository extends JpaRepository<QuizAttemptAnswerOption, UUID> {
    List<QuizAttemptAnswerOption> findByAnswerIdInOrderByAnswerIdAscPositionAsc(Collection<UUID> answerIds);
}
