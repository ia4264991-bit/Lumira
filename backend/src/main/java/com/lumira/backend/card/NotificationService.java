package com.lumira.backend.card;

import com.lumira.backend.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final CourseSpaceEventRepository events;

    public NotificationService(NotificationRepository notifications, CourseSpaceEventRepository events) {
        this.notifications = notifications;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public java.util.List<NotificationResponse> listForUser(UUID userId) {
        var inbox = notifications.findInboxForUser(userId);
        Map<UUID, CourseSpaceEvent> byId = events.findAllById(
                        inbox.stream().map(Notification::getEventId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CourseSpaceEvent::getId, Function.identity()));
        return inbox.stream().filter(n -> byId.containsKey(n.getEventId()))
                .map(n -> NotificationResponse.from(n, byId.get(n.getEventId()))).toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, UUID userId) {
        if (notifications.markReadForUser(notificationId, userId) != 1) {
            throw new ResourceNotFoundException("Notification not found");
        }
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        CourseSpaceEvent event = events.findById(notification.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        return NotificationResponse.from(notification, event);
    }
}
