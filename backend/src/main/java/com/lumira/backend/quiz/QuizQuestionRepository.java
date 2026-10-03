package com.lumira.backend.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
    List<QuizQuestion> findByQuizIdOrderByPositionAsc(UUID quizId);

    @Modifying(flushAutomatically = true)
    @Query("delete from QuizQuestion q where q.quizId=:quizId")
    int deleteByQuizId(@Param("quizId") UUID quizId);
}
