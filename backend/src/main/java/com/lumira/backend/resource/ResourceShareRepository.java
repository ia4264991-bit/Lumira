package com.lumira.backend.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceShareRepository extends JpaRepository<ResourceShare, UUID> {
    Optional<ResourceShare> findByResourceIdAndCardId(UUID resourceId, UUID cardId);
    boolean existsByResourceIdAndCardIdAndActiveTrue(UUID resourceId, UUID cardId);
    List<ResourceShare> findByCardIdAndActiveTrue(UUID cardId);
}
