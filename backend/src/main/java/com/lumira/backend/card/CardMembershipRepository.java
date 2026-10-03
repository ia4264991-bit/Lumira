package com.lumira.backend.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardMembershipRepository extends JpaRepository<CardMembership, UUID> {

    Optional<CardMembership> findFirstByCardIdAndUserIdAndStatusIn(UUID cardId, UUID userId, List<MembershipStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CardMembership m where m.id = :id")
    Optional<CardMembership> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CardMembership m where m.cardId = :cardId and m.userId = :userId and m.status in :statuses")
    Optional<CardMembership> findCurrentForUpdate(@Param("cardId") UUID cardId,
                                                   @Param("userId") UUID userId,
                                                   @Param("statuses") List<MembershipStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CardMembership m where m.cardId = :cardId and m.userId = :userId and m.status = :status")
    Optional<CardMembership> findByCardIdAndUserIdAndStatusForUpdate(@Param("cardId") UUID cardId,
                                                                      @Param("userId") UUID userId,
                                                                      @Param("status") MembershipStatus status);

    List<CardMembership> findByCardIdOrderByCreatedAtAsc(UUID cardId);

    List<CardMembership> findByCardIdAndStatusOrderByCreatedAtAsc(UUID cardId, MembershipStatus status);

    List<CardMembership> findByCardIdAndUserIdOrderByCreatedAtAsc(UUID cardId, UUID userId);

    Optional<CardMembership> findFirstByCardIdAndUserIdOrderByCreatedAtDesc(UUID cardId, UUID userId);

    List<CardMembership> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, MembershipStatus status);

    Optional<CardMembership> findFirstByCardIdAndUserIdAndStatusOrderByCreatedAtDesc(
            UUID cardId, UUID userId, MembershipStatus status);

    boolean existsByCardIdAndUserIdAndStatus(UUID cardId, UUID userId, MembershipStatus status);

    long deleteByUserId(UUID userId);

}
