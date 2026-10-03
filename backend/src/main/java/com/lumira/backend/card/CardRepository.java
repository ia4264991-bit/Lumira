package com.lumira.backend.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.ownerId = :ownerId order by c.id")
    List<Card> findByOwnerIdForUpdateOrderById(@Param("ownerId") UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Card> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.id = :id")
    Optional<Card> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Card c where c.id = :id")
    Optional<Card> findByIdForAuthorization(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Card> findByInviteToken(String inviteToken);

    List<Card> findByIdInAndIsSharedTrue(List<UUID> ids);

    @Query("select c, m.role from Card c left join CardMembership m on m.cardId = c.id and m.userId = :userId and m.status = :active " +
            "where c.isShared = true and (c.ownerId = :userId or m.id is not null) order by c.createdAt desc")
    List<Object[]> findSharedCardsForUser(@Param("userId") UUID userId,
                                          @Param("active") MembershipStatus active);
}
