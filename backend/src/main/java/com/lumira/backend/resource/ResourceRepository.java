package com.lumira.backend.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    List<Resource> findByOwner_OwningCardIdOrderByCreatedAtDesc(UUID cardId);
    List<Resource> findByOwner_OwningUserIdOrderByCreatedAtDesc(UUID userId);

    @Query(value = "select r.id from resource r " +
            "where r.owner_user_id = :userId " +
            "or r.owner_card_id in (select c.id from card c where c.owner_id = :userId) " +
            "order by r.id", nativeQuery = true)
    List<UUID> findResourceIdsOwnedByUserOrTheirCards(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    java.util.Optional<Resource> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.owner.owningCardId = :cardId order by r.id")
    List<Resource> findByOwnerCardIdForUpdateOrderById(@Param("cardId") UUID cardId);

    @Query(value = "select r.* from resource r " +
            "where r.owner_user_id = :userId " +
            "or r.owner_card_id in (select c.id from card c where c.owner_id = :userId) " +
            "order by r.id for update", nativeQuery = true)
    List<Resource> findResourcesOwnedByUserOrTheirCardsForUpdate(@Param("userId") UUID userId);
}
