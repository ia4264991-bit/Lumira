package com.lumira.backend.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query(value = """
            SELECT n.*
              FROM notification n
              LEFT JOIN card_membership m ON m.id = n.recipient_membership_id
             WHERE n.recipient_user_id = :userId OR m.user_id = :userId
             ORDER BY n.delivered_at DESC, n.id DESC
            """, nativeQuery = true)
    List<Notification> findInboxForUser(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE notification n
               SET read_at = COALESCE(n.read_at, now())
             WHERE n.id = :notificationId
               AND (n.recipient_user_id = :userId OR EXISTS (
                    SELECT 1 FROM card_membership m
                     WHERE m.id = n.recipient_membership_id AND m.user_id = :userId
               ))
            """, nativeQuery = true)
    int markReadForUser(@Param("notificationId") UUID notificationId, @Param("userId") UUID userId);
}
