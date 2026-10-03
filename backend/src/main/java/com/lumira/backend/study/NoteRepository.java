package com.lumira.backend.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {
    List<Note> findByOwner_OwningCardIdOrderByCreatedAtDesc(UUID cardId);
    List<Note> findByOwner_OwningUserIdOrderByCreatedAtDesc(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Note n where n.id = :id")
    java.util.Optional<Note> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select n.id from note n where n.owner_user_id=:userId or " +
            "n.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by n.id", nativeQuery = true)
    List<UUID> findIdsOwnedByUserOrCards(@Param("userId") UUID userId);

    @Query(value = "select n.* from note n where n.owner_user_id=:userId or " +
            "n.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by n.id for update", nativeQuery = true)
    List<Note> findOwnedByUserOrCardsForUpdate(@Param("userId") UUID userId);
}
