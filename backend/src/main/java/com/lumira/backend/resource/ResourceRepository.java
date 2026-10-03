package com.lumira.backend.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    List<Resource> findByOwner_OwningCardIdOrderByCreatedAtDesc(UUID cardId);
    List<Resource> findByOwner_OwningUserIdOrderByCreatedAtDesc(UUID userId);
}
