package com.lumira.backend.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface StudySetRepository extends JpaRepository<StudySet, UUID> {
    List<StudySet> findByOwner_OwningCardIdOrderByCreatedAtDesc(UUID cardId);
    List<StudySet> findByOwner_OwningUserIdOrderByCreatedAtDesc(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudySet s where s.id = :id")
    java.util.Optional<StudySet> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select s.id from study_set s where s.owner_user_id=:userId or " +
            "s.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by s.id", nativeQuery = true)
    List<UUID> findIdsOwnedByUserOrCards(@Param("userId") UUID userId);

    @Query(value = "select s.* from study_set s where s.owner_user_id=:userId or " +
            "s.owner_card_id in (select c.id from card c where c.owner_id=:userId) order by s.id for update", nativeQuery = true)
    List<StudySet> findOwnedByUserOrCardsForUpdate(@Param("userId") UUID userId);
}
