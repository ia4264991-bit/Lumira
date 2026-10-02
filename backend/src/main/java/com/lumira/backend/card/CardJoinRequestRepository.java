package com.lumira.backend.card;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardJoinRequestRepository extends JpaRepository<CardJoinRequest, UUID> {
    List<CardJoinRequest> findByCardIdOrderByCreatedAtAsc(UUID cardId);
    List<CardJoinRequest> findByCardIdAndStatusOrderByCreatedAtAsc(UUID cardId, JoinRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CardJoinRequest r where r.id = :id")
    Optional<CardJoinRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("select r from CardJoinRequest r where r.cardId = :cardId and r.inviteTokenVersion = :version and r.status = :status")
    List<CardJoinRequest> findByCardIdAndInviteTokenVersionAndStatus(@Param("cardId") UUID cardId,
                                                                      @Param("version") long version,
                                                                      @Param("status") JoinRequestStatus status);
}
