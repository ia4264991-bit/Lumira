package com.lumira.backend.flashcard;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashcardSetRepository extends JpaRepository<FlashcardSet, UUID> {
    List<FlashcardSet> findByOwner_OwningCardIdOrderByCreatedAtDesc(UUID cardId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FlashcardSet s where s.id=:id")
    Optional<FlashcardSet> findByIdForUpdate(@Param("id") UUID id);
    @Query(value = "select s.id from flashcard_set s where s.owner_user_id=:userId or " +
            "s.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by s.id", nativeQuery = true)
    List<UUID> findIdsOwnedByUserOrCards(@Param("userId") UUID userId);
    @Query(value = "select s.* from flashcard_set s where s.owner_user_id=:userId or " +
            "s.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by s.id for update", nativeQuery = true)
    List<FlashcardSet> findOwnedByUserOrCardsForUpdate(@Param("userId") UUID userId);
}
