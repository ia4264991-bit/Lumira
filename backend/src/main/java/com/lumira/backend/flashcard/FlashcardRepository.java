package com.lumira.backend.flashcard;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashcardRepository extends JpaRepository<Flashcard, UUID> {
    List<Flashcard> findByFlashcardSetIdOrderByPositionAsc(UUID setId);
    List<Flashcard> findByFlashcardSetIdOrderByPositionAscIdAsc(UUID setId);
    List<Flashcard> findByFlashcardSetIdInOrderByFlashcardSetIdAscPositionAscIdAsc(List<UUID> setIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flashcard f where f.id=:id")
    Optional<Flashcard> findByIdForUpdate(@Param("id") UUID id);
}
