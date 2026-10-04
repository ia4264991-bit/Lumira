package com.lumira.backend.security;

import com.lumira.backend.user.User;
import com.lumira.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseTokenResolverTest {

    private final FirebaseIdTokenVerifier verifier = mock(FirebaseIdTokenVerifier.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FirebaseTokenResolver resolver = new FirebaseTokenResolver(verifier, users);

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(verifier, users);
    }

    @Test
    void verifiedFirebaseUidResolvesToInternalVisionUuid() {
        UUID visionUserId = UUID.randomUUID();
        User user = mock(User.class);
        when(verifier.verifiedUid("valid-id-token")).thenReturn(Optional.of("firebase-uid-1"));
        when(users.findByFirebaseUid("firebase-uid-1")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(visionUserId);

        assertThat(resolver.resolve("valid-id-token"))
                .contains(new AuthenticatedUser(visionUserId));
    }

    @Test
    void invalidTokenIsRejected() {
        when(verifier.verifiedUid("invalid-token")).thenReturn(Optional.empty());

        assertThat(resolver.resolve("invalid-token")).isEmpty();
        verify(users, never()).findByFirebaseUid(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void expiredTokenIsRejected() {
        // The Admin SDK verifier maps its expired-token verification failure to empty.
        when(verifier.verifiedUid("expired-token")).thenReturn(Optional.empty());

        assertThat(resolver.resolve("expired-token")).isEmpty();
        verify(users, never()).findByFirebaseUid(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unknownFirebaseUidIsRejectedWithoutEmailLookupOrProvisioning() {
        when(verifier.verifiedUid("valid-unmapped-token")).thenReturn(Optional.of("unmapped-uid"));
        when(users.findByFirebaseUid("unmapped-uid")).thenReturn(Optional.empty());

        assertThat(resolver.resolve("valid-unmapped-token")).isEmpty();
        verify(users).findByFirebaseUid("unmapped-uid");
        verify(users, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }
}
