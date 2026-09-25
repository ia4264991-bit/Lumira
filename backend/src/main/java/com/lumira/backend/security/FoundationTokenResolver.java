package com.lumira.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Baseline foundation token resolver for B0 milestone.
 *
 * <p>Supports UUID-formatted tokens for integration testing and local development.
 * In milestone B1, this will be superseded by the real identity/token implementation.
 */
@Component
@ConditionalOnMissingBean(value = TokenResolver.class, ignored = FoundationTokenResolver.class)
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
            log.debug("Token is not a valid UUID principal: {}", token);
            return Optional.empty();
        }
    }
}
