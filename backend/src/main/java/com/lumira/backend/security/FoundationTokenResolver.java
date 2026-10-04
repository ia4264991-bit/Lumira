package com.lumira.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Baseline foundation token resolver for B0 milestone.
 *
 * <p>Test-profile-only UUID principal resolver. Never enable it in a deployed runtime.
 */
@Component
@Profile("test")
public class FoundationTokenResolver implements TokenResolver {

    private static final Logger log = LoggerFactory.getLogger(FoundationTokenResolver.class);

    @Override
    public Optional<AuthenticatedUser> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            UUID userId = UUID.fromString(token.trim());
            return Optional.of(new AuthenticatedUser(userId));
        } catch (IllegalArgumentException e) {
            log.debug("Token is not a valid UUID principal");
            return Optional.empty();
        }
    }
}
