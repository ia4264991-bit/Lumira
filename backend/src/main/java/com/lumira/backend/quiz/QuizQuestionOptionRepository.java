package com.lumira.backend.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuizQuestionOptionRepository extends JpaRepository<QuizQuestionOption, UUID> {
    List<QuizQuestionOption> findByQuestionIdInOrderByQuestionIdAscPositionAsc(Collection<UUID> questionIds);
    List<QuizQuestionOption> findByQuestionIdOrderByPositionAsc(UUID questionId);
}
