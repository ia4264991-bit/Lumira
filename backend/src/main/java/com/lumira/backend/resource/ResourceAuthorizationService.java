package com.lumira.backend.resource;

import com.lumira.backend.card.Card;
import com.lumira.backend.card.CardMembership;
import com.lumira.backend.card.CardMembershipRepository;
import com.lumira.backend.card.CardRepository;
import com.lumira.backend.card.MembershipRole;
import com.lumira.backend.card.MembershipStatus;
import com.lumira.backend.common.domain.ArtifactOwner;
import com.lumira.backend.common.error.ForbiddenException;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Live, persisted authorization policy shared by artifact and sharing operations (AD-041/056). */
@Service
public class ResourceAuthorizationService {
    private final ResourceShareRepository shares;
    private final CardRepository cards;
    private final CardMembershipRepository memberships;
    private final UserRepository users;

    public ResourceAuthorizationService(ResourceShareRepository shares, CardRepository cards,
            CardMembershipRepository memberships, UserRepository users) {
        this.shares = shares;
        this.cards = cards;
        this.memberships = memberships;
        this.users = users;
    }

    public Card requireCardReadable(UUID cardId, UUID actorId) {
        requireExistingUser(actorId, "Card not found");
        Card card = cards.findByIdForAuthorization(cardId).orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (card.isOwnedBy(actorId)) return card;
        if (!card.isShared() || !memberships.existsByCardIdAndUserIdAndStatus(
                cardId, actorId, MembershipStatus.ACTIVE)) {
            throw new ResourceNotFoundException("Card not found");
        }
        return card;
    }

    public Card requireCardUpload(UUID cardId, UUID actorId) {
        Card card = requireCardReadable(cardId, actorId);
        if (!card.isShared()) return card;
        requireUploadRole(card, actorId);
        return card;
    }

    public Card requireCardUploadForUpdate(UUID cardId, UUID actorId) {
        requireExistingUser(actorId, "Card not found");
        Card card = cards.findByIdForUpdate(cardId).orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (card.isOwnedBy(actorId) && !card.isShared()) return card;
        if (!card.isShared() || (!card.isOwnedBy(actorId) && !memberships.existsByCardIdAndUserIdAndStatus(
                cardId, actorId, MembershipStatus.ACTIVE))) throw new ResourceNotFoundException("Card not found");
        requireUploadRole(card, actorId);
        return card;
    }

    private void requireUploadRole(Card card, UUID actorId) {
        MembershipRole role = activeRole(card, actorId);
        if (role != MembershipRole.OWNER && role != MembershipRole.ADMIN) {
            throw new ForbiddenException("Owner or active Admin role is required to create content in a Course Space");
        }
    }

    public void requireActiveMember(Card card, UUID actorId) {
        requireExistingUser(actorId, "Course Space not found");
        if (!card.isShared()) throw new ResourceNotFoundException("Course Space not found");
        if (card.isOwnedBy(actorId)) return;
        if (!memberships.existsByCardIdAndUserIdAndStatus(card.getId(), actorId, MembershipStatus.ACTIVE)) {
            throw new ResourceNotFoundException("Course Space not found");
        }
    }

    public MembershipRole requireOwnerOrAdmin(Card card, UUID actorId) {
        requireActiveMember(card, actorId);
        MembershipRole role = activeRole(card, actorId);
        if (role != MembershipRole.OWNER && role != MembershipRole.ADMIN) {
            throw new ForbiddenException("Only the Course Space Owner or an active Admin may perform this action");
        }
        return role;
    }

    public void requireResourceOwner(Resource resource, UUID actorId) {
        requireArtifactOwner(resource.getOwner(), "Resource not found", actorId);
    }

    public void requireArtifactOwner(ArtifactOwner owner, String notFoundMessage, UUID actorId) {
        requireExistingUser(actorId, notFoundMessage);
        if (isOwner(owner, actorId)) return;
        throw new ResourceNotFoundException(notFoundMessage);
    }

    public void requireArtifactReadable(ArtifactOwner owner, ArtifactType type, UUID artifactId,
            String notFoundMessage, UUID actorId) {
        requireExistingUser(actorId, notFoundMessage);
        if (isOwner(owner, actorId)) return;
        for (ResourceShare share : shares.findActiveForArtifact(type.apiValue(), artifactId)) {
            Card card = cards.findByIdForAuthorization(share.getCardId()).orElse(null);
            if (card == null || !card.isShared()) continue;
            if (card.isOwnedBy(actorId) || memberships.existsByCardIdAndUserIdAndStatus(
                    card.getId(), actorId, MembershipStatus.ACTIVE)) return;
        }
        throw new ResourceNotFoundException(notFoundMessage);
    }

    public void requireResourceReadable(Resource resource, UUID actorId) {
        requireArtifactReadable(resource.getOwner(), ArtifactType.RESOURCE, resource.getId(), "Resource not found", actorId);
    }

    public boolean isOwner(Resource resource, UUID actorId) {
        return isOwner(resource.getOwner(), actorId);
    }

    public boolean isOwner(ArtifactOwner owner, UUID actorId) {
        if (owner.isUserOwned()) return owner.getOwningUserId().equals(actorId);
        return cards.findByIdAndOwnerId(owner.getOwningCardId(), actorId).isPresent();
    }

    public Set<UUID> ownedCardIds(UUID actorId, Collection<UUID> candidateCardIds) {
        if (candidateCardIds.isEmpty()) return Set.of();
        return cards.findByIdInAndOwnerId(candidateCardIds.stream().distinct().toList(), actorId).stream()
                .map(Card::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private MembershipRole activeRole(Card card, UUID actorId) {
        if (card.isOwnedBy(actorId)) return MembershipRole.OWNER;
        CardMembership membership = memberships.findFirstByCardIdAndUserIdAndStatusIn(
                        card.getId(), actorId, List.of(MembershipStatus.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("Course Space not found"));
        return membership.getRole();
    }

    private void requireExistingUser(UUID userId, String message) {
        if (users.findByIdForAuthorization(userId).isEmpty()) throw new ResourceNotFoundException(message);
    }
}
