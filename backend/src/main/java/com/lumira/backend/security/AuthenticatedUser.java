package com.lumira.backend.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the authenticated caller principal in the backend request context.
 *
 * <p>Builds the minimal identity boundary needed for B0/B1 to attribute requests
 * to a stable user ID without locking into a specific token/session technology.
 */
public record AuthenticatedUser(UUID userId) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId cannot be null");
    }
}
