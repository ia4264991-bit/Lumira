package com.lumira.backend.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceShareRepository extends JpaRepository<ResourceShare, UUID> {
    Optional<ResourceShare> findByResourceIdAndCardId(UUID resourceId, UUID cardId);
    boolean existsByResourceIdAndCardIdAndActiveTrue(UUID resourceId, UUID cardId);
    List<ResourceShare> findByCardIdAndActiveTrue(UUID cardId);

    List<ResourceShare> findByCardIdAndActiveTrueOrderByResourceIdAsc(UUID cardId);

    List<ResourceShare> findByResourceIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ResourceShare s where s.resourceId = :resourceId and s.cardId = :cardId")
    Optional<ResourceShare> findByResourceIdAndCardIdForUpdate(@Param("resourceId") UUID resourceId,
                                                                @Param("cardId") UUID cardId);
}
