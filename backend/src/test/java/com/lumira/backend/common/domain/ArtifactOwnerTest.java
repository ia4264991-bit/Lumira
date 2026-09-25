package com.lumira.backend.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactOwnerTest {

    @Test
    @DisplayName("AD-057: Card-owned artifact has non-null cardId and null userId")
    void cardOwnedArtifact() {
        UUID cardId = UUID.randomUUID();
        ArtifactOwner owner = ArtifactOwner.forCard(cardId);

        assertThat(owner.isCardOwned()).isTrue();
        assertThat(owner.isUserOwned()).isFalse();
        assertThat(owner.getOwningCardId()).isEqualTo(cardId);
        assertThat(owner.getOwningUserId()).isNull();
    }

    @Test
    @DisplayName("AD-057: User-owned artifact has non-null userId and null cardId")
    void userOwnedArtifact() {
        UUID userId = UUID.randomUUID();
        ArtifactOwner owner = ArtifactOwner.forUser(userId);

        assertThat(owner.isUserOwned()).isTrue();
        assertThat(owner.isCardOwned()).isFalse();
        assertThat(owner.getOwningUserId()).isEqualTo(userId);
        assertThat(owner.getOwningCardId()).isNull();
    }

    @Test
    @DisplayName("AD-057: Invariant fails if both cardId and userId are set")
    void invalidBothOwnersSet() {
        ArtifactOwner owner = new ArtifactOwner();
        owner.setOwningCardId(UUID.randomUUID());
        owner.setOwningUserId(UUID.randomUUID());

        assertThatThrownBy(owner::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AD-057 invariant violation");
    }

    @Test
    @DisplayName("AD-057: Invariant fails if neither cardId nor userId is set")
    void invalidNeitherOwnerSet() {
        ArtifactOwner owner = new ArtifactOwner();

        assertThatThrownBy(owner::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AD-057 invariant violation");
    }
}
