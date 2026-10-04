package com.lumira.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.AuthErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Uses Firebase Admin SDK cryptographic verification; never decodes client claims locally. */
@Component
@Profile("!test")
public class FirebaseAdminIdTokenVerifier implements FirebaseIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminIdTokenVerifier.class);
    private final FirebaseAuth firebaseAuth;

    public FirebaseAdminIdTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public Optional<String> verifiedUid(String idToken) {
        if (idToken == null || idToken.isBlank()) return Optional.empty();
        try {
            return Optional.of(firebaseAuth.verifyIdToken(idToken).getUid());
        } catch (FirebaseAuthException e) {
            AuthErrorCode code = e.getAuthErrorCode();
            if (code == AuthErrorCode.INVALID_ID_TOKEN || code == AuthErrorCode.EXPIRED_ID_TOKEN
                    || code == AuthErrorCode.REVOKED_ID_TOKEN) {
                log.debug("Firebase ID token was rejected by the Admin SDK");
                return Optional.empty();
            }
            throw new IllegalStateException("Firebase ID token verification could not be completed", e);
        } catch (IllegalArgumentException e) {
            log.debug("Firebase ID token verification failed");
            return Optional.empty();
        }
    }
}
