package com.lumira.backend.quiz;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {
    List<Quiz> findByOwner_OwningCardIdOrderByCreatedAtDesc(UUID cardId);
    List<Quiz> findByOwner_OwningUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quiz q where q.id=:id")
    Optional<Quiz> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select q.id from quiz q where q.owner_user_id=:userId or " +
            "q.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by q.id", nativeQuery = true)
    List<UUID> findIdsOwnedByUserOrCards(@Param("userId") UUID userId);

    @Query(value = "select q.* from quiz q where q.owner_user_id=:userId or " +
            "q.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by q.id for update", nativeQuery = true)
    List<Quiz> findOwnedByUserOrCardsForUpdate(@Param("userId") UUID userId);
}
