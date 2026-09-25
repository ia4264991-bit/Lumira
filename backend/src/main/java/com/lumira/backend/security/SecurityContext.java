package com.lumira.backend.security;

import java.util.Optional;

/**
 * Thread-local holder for the currently authenticated user in the request lifecycle.
 */
public final class SecurityContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private SecurityContext() {
    }

    public static void setCurrentUser(AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    public static Optional<AuthenticatedUser> getCurrentUser() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static AuthenticatedUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(() ->
                new com.lumira.backend.common.error.UnauthorizedException("Authentication required"));
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
