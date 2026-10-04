package com.lumira.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebaseAdminIdTokenVerifierTest {

    private final FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
    private final FirebaseAdminIdTokenVerifier verifier = new FirebaseAdminIdTokenVerifier(firebaseAuth);

    @Test
    void returnsUidFromAdminSdkVerifiedToken() throws Exception {
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(firebaseAuth.verifyIdToken("signed-token")).thenReturn(decoded);
        when(decoded.getUid()).thenReturn("verified-uid");

        assertThat(verifier.verifiedUid("signed-token")).contains("verified-uid");
    }

    @Test
    void invalidTokenRejectedByAdminSdkIsNotResolved() throws Exception {
        when(firebaseAuth.verifyIdToken("invalid-token"))
                .thenThrow(new FirebaseAuthException(ErrorCode.UNAUTHENTICATED, "invalid", null, null,
                        AuthErrorCode.INVALID_ID_TOKEN));

        assertThat(verifier.verifiedUid("invalid-token")).isEmpty();
    }

    @Test
    void expiredTokenRejectedByAdminSdkIsNotResolved() throws Exception {
        when(firebaseAuth.verifyIdToken("expired-token"))
                .thenThrow(new FirebaseAuthException(ErrorCode.UNAUTHENTICATED, "expired", null, null,
                        AuthErrorCode.EXPIRED_ID_TOKEN));

        assertThat(verifier.verifiedUid("expired-token")).isEmpty();
    }

    @Test
    void blankTokenIsRejectedWithoutCallingAdminSdk() throws Exception {
        assertThat(verifier.verifiedUid(" ")).isEqualTo(Optional.empty());
        org.mockito.Mockito.verifyNoInteractions(firebaseAuth);
    }
}
