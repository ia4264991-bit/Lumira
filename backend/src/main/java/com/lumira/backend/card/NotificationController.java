package com.lumira.backend.card;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
@RequireAuth
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public List<NotificationResponse> list(@CurrentUser AuthenticatedUser user) {
        return service.listForUser(user.userId());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        return service.markRead(id, user.userId());
    }
}
