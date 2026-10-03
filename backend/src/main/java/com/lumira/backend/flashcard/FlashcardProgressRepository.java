package com.lumira.backend.flashcard;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashcardProgressRepository extends JpaRepository<FlashcardProgress, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FlashcardProgress p where p.userId=:userId and p.flashcardId=:flashcardId")
    Optional<FlashcardProgress> findForUpdate(@Param("userId") UUID userId, @Param("flashcardId") UUID flashcardId);
    List<FlashcardProgress> findByUserIdAndFlashcardIdInOrderByFlashcardIdAsc(UUID userId, List<UUID> cardIds);
    List<FlashcardProgress> findByUserIdOrderByReviewedAtDesc(UUID userId);
    void deleteByUserId(UUID userId);
}
