package com.lumira.backend.security;

import com.lumira.backend.user.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Firebase token boundary: verified UID to durable Vision UUID principal only. */
@Component
@Profile("!test")
public class FirebaseTokenResolver implements TokenResolver {

    private final FirebaseIdTokenVerifier verifier;
    private final UserRepository users;

    public FirebaseTokenResolver(FirebaseIdTokenVerifier verifier, UserRepository users) {
        this.verifier = verifier;
        this.users = users;
    }

    @Override
    public Optional<AuthenticatedUser> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return verifier.verifiedUid(token)
                .flatMap(users::findByFirebaseUid)
                .map(user -> new AuthenticatedUser(user.getId()));
    }
}
