package com.lumira.backend.security;

import java.util.Optional;

/** Verifies a Firebase ID token and returns its verified Firebase UID. */
@FunctionalInterface
public interface FirebaseIdTokenVerifier {
    Optional<String> verifiedUid(String idToken);
}
