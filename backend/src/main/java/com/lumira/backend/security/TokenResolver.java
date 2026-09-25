package com.lumira.backend.security;

import java.util.Optional;

/**
 * Interface for resolving raw authentication tokens into authenticated user principals.
 *
 * <p>Provides the abstraction point allowing B1 to plug in the minimal identity/token
 * architecture without changing controller signatures or security boundary code.
 */
public interface TokenResolver {

    /**
     * Resolve a token from the Authorization header into an {@link AuthenticatedUser}.
     *
     * @param token raw token string extracted from 'Bearer <token>'
     * @return optional containing the AuthenticatedUser if valid, empty otherwise
     */
    Optional<AuthenticatedUser> resolve(String token);
}
