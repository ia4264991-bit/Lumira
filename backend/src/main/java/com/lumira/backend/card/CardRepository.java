package com.lumira.backend.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Card} entities.
 *
 * <p>Queries are owner-scoped; the service layer is responsible for ensuring
 * that only an authenticated user's own cards are returned (AD-041/AD-056).
 */
@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
