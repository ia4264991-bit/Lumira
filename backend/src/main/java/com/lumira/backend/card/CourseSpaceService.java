package com.lumira.backend.card;

import com.lumira.backend.common.error.ConflictException;
import com.lumira.backend.common.error.ForbiddenException;
import com.lumira.backend.common.error.GoneException;
import com.lumira.backend.common.error.ResourceNotFoundException;
import com.lumira.backend.resource.ResourceShareService;
import com.lumira.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

/** B2 lifecycle and authorization operations for Course Spaces (shared Cards). */
@Service
@Transactional
public class CourseSpaceService {

    private static final List<MembershipStatus> CURRENT = List.of(MembershipStatus.ACTIVE, MembershipStatus.INVITED);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardRepository cardRepository;
    private final CardMembershipRepository membershipRepository;
    private final CardJoinRequestRepository joinRequestRepository;
    private final CourseSpaceEventRepository eventRepository;
    private final CourseSpaceEventService eventService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final ResourceShareService resourceShareService;
    private final String inviteBaseUrl;

    public CourseSpaceService(CardRepository cardRepository,
                              CardMembershipRepository membershipRepository,
                              CardJoinRequestRepository joinRequestRepository,
                              CourseSpaceEventRepository eventRepository,
                              CourseSpaceEventService eventService,
                              UserRepository userRepository,
                              EntityManager entityManager,
                              ResourceShareService resourceShareService,
                              @Value("${lumira.invite.base-url:https://lumira.app}") String inviteBaseUrl) {
        this.cardRepository = cardRepository;
        this.membershipRepository = membershipRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.resourceShareService = resourceShareService;
        this.inviteBaseUrl = inviteBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public Card getAccessibleCard(UUID cardId, UUID userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (card.isOwnedBy(userId)) return card;
        if (!card.isShared() || !membershipRepository.existsByCardIdAndUserIdAndStatus(
                cardId, userId, MembershipStatus.ACTIVE)) {
            throw new ResourceNotFoundException("Card not found");
        }
        return card;
    }

    @Transactional(readOnly = true)
    public List<SharedCardResponse> listSharedCards(UUID userId, int page, int pageSize) {
        return cardRepository.findSharedCardsForUser(userId, MembershipStatus.ACTIVE,
                        PageRequest.of(page, pageSize)).getContent()
                .stream().map(row -> SharedCardResponse.from((Card) row[0],
                        row[1] == null ? MembershipRole.OWNER : (MembershipRole) row[1],
                        row[2] == null ? ((Card) row[0]).getId() : (UUID) row[2])).toList();
    }

    public Card enableSharing(UUID cardId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        if (!card.isShared()) {
            card.setShared(true);
            resourceShareService.activateResourcesForCard(card.getId());
            CardMembership ownerMembership = membershipRepository.findFirstByCardIdAndUserIdAndStatusIn(
                    card.getId(), actorId, List.of(MembershipStatus.ACTIVE)).orElse(null);
            if (ownerMembership == null) {
                membershipRepository.save(new CardMembership(card.getId(), actorId, card.getId(),
                        MembershipStatus.ACTIVE, MembershipRole.OWNER));
                emit(card.getId(), "MEMBER_JOINED", actorId, Map.of("userId", actorId, "role", "OWNER"));
            } else if (ownerMembership.getRole() != MembershipRole.OWNER
                    || !ownerMembership.getMemberCardId().equals(card.getId())) {
                throw new ConflictException("Shared Card has an invalid Owner membership");
            }
        } else if (!membershipRepository.existsByCardIdAndUserIdAndStatus(cardId, actorId, MembershipStatus.ACTIVE)) {
            throw new ConflictException("Shared Card is missing its active Owner membership");
        }
        return card;
    }

    public ShareLinkResponse resetShareLink(UUID cardId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        requireShared(card);
        long oldVersion = card.getInviteTokenVersion();
        String token = newToken();
        card.resetInviteLink(token);
        invalidatePendingRequests(cardId, oldVersion, actorId);
        return shareLinkResponse(card);
    }

    @Transactional(readOnly = true)
    public ShareLinkResponse getShareLink(UUID cardId, UUID actorId) {
        Card card = requireSharedCard(cardId);
        requireOwnerOrAdmin(card, actorId);
        if (card.getInviteToken() == null) throw new ResourceNotFoundException("Invite link not found");
        return shareLinkResponse(card);
    }

    public Card setRequireApproval(UUID cardId, UUID actorId, boolean requireApproval) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        requireShared(card);
        card.setRequireApproval(requireApproval);
        return card;
    }

    public JoinOutcome joinByLink(String shareToken, UUID userId) {
        Card card = cardRepository.findByInviteToken(shareToken)
            .orElseThrow(() -> new GoneException("Invite link is invalid or has been reset"));
        card = lockCard(card.getId());
        if (!card.isShared() || !shareToken.equals(card.getInviteToken())) {
            throw new GoneException("Invite link is invalid or has been reset");
        }
        requireUser(userId);
        CardMembership existing = membershipRepository.findCurrentForUpdate(card.getId(), userId, CURRENT).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == MembershipStatus.ACTIVE) {
                Card memberCard = cardRepository.findByIdAndOwnerId(existing.getMemberCardId(), userId)
                        .orElseThrow(() -> new ConflictException("Membership member Card is missing or has a different owner"));
                return new JoinOutcome(memberCard, null);
            }
            throw new ConflictException("An invitation is already pending for this Course Space");
        }
        if (card.isRequireApproval()) {
            CardJoinRequest request = joinRequestRepository.save(
                    new CardJoinRequest(card.getId(), userId, card.getInviteTokenVersion()));
            return new JoinOutcome(null, request);
        }
        Card memberCard = findOrCreateMemberCard(card, userId);
        CardMembership membership = membershipRepository.save(new CardMembership(card.getId(), userId,
                memberCard.getId(), MembershipStatus.ACTIVE, MembershipRole.MEMBER));
        emit(card.getId(), "MEMBER_JOINED", userId, Map.of("userId", userId, "membershipId", membership.getId()));
        return new JoinOutcome(memberCard, null);
    }

    public InviteOutcome inviteUser(UUID cardId, UUID actorId, UUID invitedUserId) {
        Card card = lockCard(cardId);
        requireShared(card);
        requireOwnerOrAdmin(card, actorId);
        requireUser(invitedUserId);
        CardMembership existing = membershipRepository.findCurrentForUpdate(cardId, invitedUserId, CURRENT).orElse(null);
        if (existing != null) return new InviteOutcome(existing, false);
        Card memberCard = findOrCreateMemberCard(card, invitedUserId);
        CardMembership invited = membershipRepository.save(new CardMembership(cardId, invitedUserId,
                memberCard.getId(), MembershipStatus.INVITED, MembershipRole.MEMBER));
        emit(cardId, "MEMBER_INVITED", actorId,
                Map.of("userId", invitedUserId, "membershipId", invited.getId()));
        return new InviteOutcome(invited, true);
    }

    @Transactional(readOnly = true)
    public List<DirectInvitationResponse> listMyInvitations(UUID userId) {
        return membershipRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, MembershipStatus.INVITED)
                .stream().map(DirectInvitationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CardMembership getMyInvitation(UUID membershipId, UUID userId) {
        CardMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        if (!membership.getUserId().equals(userId) || membership.getStatus() != MembershipStatus.INVITED) {
            throw new ResourceNotFoundException("Invitation not found");
        }
        return membership;
    }

    public CardMembership acceptInvitation(UUID membershipId, UUID userId) {
        CardMembership snapshot = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        if (!snapshot.getUserId().equals(userId)) throw new ResourceNotFoundException("Invitation not found");
        Card card = lockCard(snapshot.getCardId());
        CardMembership membership = membershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        requireOwnedInvitation(membership, userId);
        requireShared(card);
        if (membership.getStatus() != MembershipStatus.INVITED) {
            throw new ConflictException("Invitation is no longer pending");
        }
        requireUser(userId);
        cardRepository.findByIdAndOwnerId(membership.getMemberCardId(), userId)
                .orElseThrow(() -> new ConflictException("Invitation member Card is missing or has a different owner"));
        membership.accept();
        emit(card.getId(), "MEMBER_INVITATION_ACCEPTED", userId,
                Map.of("userId", userId, "membershipId", membershipId));
        return membership;
    }

    public CardMembership declineInvitation(UUID membershipId, UUID userId) {
        CardMembership snapshot = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        if (!snapshot.getUserId().equals(userId)) throw new ResourceNotFoundException("Invitation not found");
        Card card = lockCard(snapshot.getCardId());
        CardMembership membership = membershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        requireOwnedInvitation(membership, userId);
        if (membership.getStatus() != MembershipStatus.INVITED) {
            throw new ConflictException("Invitation is no longer pending");
        }
        membership.decline();
        emit(card.getId(), "MEMBER_INVITATION_DECLINED", userId,
                Map.of("userId", userId, "membershipId", membershipId));
        return membership;
    }

    public CardMembership withdrawInvitation(UUID cardId, UUID membershipId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        CardMembership membership = membershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        if (!membership.getCardId().equals(cardId) || membership.getStatus() != MembershipStatus.INVITED) {
            throw new ResourceNotFoundException("Invitation not found");
        }
        membership.withdraw();
        emit(cardId, "MEMBER_INVITATION_WITHDRAWN", actorId,
                Map.of("userId", membership.getUserId(), "membershipId", membershipId));
        return membership;
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> listJoinRequests(UUID cardId, UUID actorId) {
        Card card = requireSharedCard(cardId);
        requireOwnerOrAdmin(card, actorId);
        return joinRequestRepository.findByCardIdOrderByCreatedAtAsc(cardId)
                .stream().map(JoinRequestResponse::from).toList();
    }

    public Card approveJoinRequest(UUID cardId, UUID requestId, UUID actorId) {
        Card card = lockCard(cardId);
        requireShared(card);
        requireOwnerOrAdmin(card, actorId);
        CardJoinRequest request = joinRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found"));
        if (!request.getCardId().equals(cardId)) throw new ResourceNotFoundException("Join request not found");
        if (request.getStatus() != JoinRequestStatus.PENDING) throw new ConflictException("Join request is no longer pending");
        if (request.getInviteTokenVersion() != card.getInviteTokenVersion()) {
            throw new GoneException("Join request was invalidated by invite-link reset");
        }
        requireUser(request.getRequestingUserId());
        if (membershipRepository.findCurrentForUpdate(cardId, request.getRequestingUserId(), CURRENT).isPresent()) {
            throw new ConflictException("User already has a current membership");
        }
        Card memberCard = findOrCreateMemberCard(card, request.getRequestingUserId());
        CardMembership membership = membershipRepository.save(new CardMembership(cardId,
                request.getRequestingUserId(), memberCard.getId(), MembershipStatus.ACTIVE, MembershipRole.MEMBER));
        request.resolve(JoinRequestStatus.APPROVED, actorId);
        emit(cardId, "MEMBER_JOINED", actorId,
                Map.of("userId", membership.getUserId(), "membershipId", membership.getId()));
        return memberCard;
    }

    public CardJoinRequest rejectJoinRequest(UUID cardId, UUID requestId, UUID actorId) {
        Card card = lockCard(cardId);
        requireShared(card);
        requireOwnerOrAdmin(card, actorId);
        CardJoinRequest request = joinRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found"));
        if (!request.getCardId().equals(cardId)) throw new ResourceNotFoundException("Join request not found");
        if (request.getStatus() != JoinRequestStatus.PENDING) throw new ConflictException("Join request is no longer pending");
        if (request.getInviteTokenVersion() != card.getInviteTokenVersion()) {
            request.resolve(JoinRequestStatus.INVALIDATED, actorId);
            return request;
        }
        request.resolve(JoinRequestStatus.REJECTED, actorId);
        return request;
    }

    @Transactional(readOnly = true)
    public List<CardMembershipResponse> listMembers(UUID cardId, UUID actorId) {
        Card card = requireSharedCard(cardId);
        requireActiveMember(card, actorId);
        return membershipRepository.findByCardIdOrderByCreatedAtAsc(cardId)
                .stream().map(CardMembershipResponse::from).toList();
    }

    public CardMembership promote(UUID cardId, UUID targetUserId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        CardMembership target = activeMembership(cardId, targetUserId);
        try { target.promote(); }
        catch (IllegalStateException ex) { throw new ConflictException(ex.getMessage()); }
        emit(cardId, "MEMBER_PROMOTED", actorId, Map.of("userId", targetUserId, "membershipId", target.getId()));
        return target;
    }

    public CardMembership demote(UUID cardId, UUID targetUserId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        CardMembership target = activeMembership(cardId, targetUserId);
        try { target.demote(); }
        catch (IllegalStateException ex) { throw new ConflictException(ex.getMessage()); }
        emit(cardId, "MEMBER_DEMOTED", actorId, Map.of("userId", targetUserId, "membershipId", target.getId()));
        return target;
    }

    public CardMembership transferOwnership(UUID cardId, UUID targetUserId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        requireShared(card);
        if (actorId.equals(targetUserId)) {
            throw new ConflictException("Owner cannot transfer ownership to themselves");
        }

        CardMembership formerOwner = membershipRepository.findByCardIdAndUserIdAndStatusForUpdate(
                        cardId, actorId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ConflictException("Current Owner membership is missing"));
        if (formerOwner.getRole() != MembershipRole.OWNER || !formerOwner.getMemberCardId().equals(cardId)) {
            throw new ConflictException("Current Owner membership is inconsistent");
        }

        CardMembership newOwner = membershipRepository.findByCardIdAndUserIdAndStatusForUpdate(
                        cardId, targetUserId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ConflictException("Ownership target must be an active member"));
        if (newOwner.getRole() == MembershipRole.OWNER) {
            throw new ConflictException("Ownership target is already the Owner");
        }

        Card formerOwnerCard = cardRepository.save(new Card(actorId, card.getName(), card.getColor()));
        formerOwner.setRole(MembershipRole.ADMIN);
        formerOwner.setMemberCardId(formerOwnerCard.getId());
        // Release the partial unique ACTIVE OWNER index before assigning the role to the target.
        // V4's deferred constraint trigger checks the complete, consistent state at commit.
        entityManager.flush();

        card.transferOwnership(targetUserId);
        newOwner.setRole(MembershipRole.OWNER);
        newOwner.setMemberCardId(cardId);
        emit(cardId, "OWNERSHIP_TRANSFERRED", actorId,
                Map.of("fromUserId", actorId, "toUserId", targetUserId,
                        "fromMembershipId", formerOwner.getId(), "toMembershipId", newOwner.getId()));
        return newOwner;
    }

    public CardMembership removeMember(UUID cardId, UUID targetUserId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        CardMembership target = activeMembership(cardId, targetUserId);
        if (target.getRole() == MembershipRole.OWNER) throw new ForbiddenException("Owner cannot be removed");
        try { target.remove(); }
        catch (IllegalStateException ex) { throw new ConflictException(ex.getMessage()); }
        emit(cardId, "MEMBER_REMOVED", actorId, Map.of("userId", targetUserId, "membershipId", target.getId()));
        return target;
    }

    public CardMembership leave(UUID cardId, UUID userId) {
        Card card = lockCard(cardId);
        requireShared(card);
        CardMembership membership = activeMembership(cardId, userId);
        if (membership.getRole() == MembershipRole.OWNER) {
            throw new ConflictException("Owner must transfer ownership before leaving");
        }
        membership.leave();
        emit(cardId, "MEMBER_LEFT", userId, Map.of("userId", userId));
        return membership;
    }

    public Card dissolve(UUID cardId, UUID actorId) {
        Card card = lockCard(cardId);
        requireOwner(card, actorId);
        requireShared(card);
        resourceShareService.deactivateAllForCard(cardId);
        card.setShared(false);
        emit(cardId, "COURSE_SPACE_DISSOLVED", actorId, Map.of());
        return card;
    }

    @Transactional(readOnly = true)
    public List<CourseSpaceEventResponse> listEvents(UUID cardId, UUID actorId) {
        Card card = requireSharedCard(cardId);
        requireActiveMember(card, actorId);
        return eventRepository.findByCardIdOrderByCreatedAtDesc(cardId)
                .stream().map(CourseSpaceEventResponse::from).toList();
    }

    private CardMembership activeMembership(UUID cardId, UUID userId) {
        return membershipRepository.findFirstByCardIdAndUserIdAndStatusIn(cardId, userId,
                        List.of(MembershipStatus.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("Active membership not found"));
    }

    private Card findOrCreateMemberCard(Card courseSpaceCard, UUID userId) {
        var history = membershipRepository.findFirstByCardIdAndUserIdOrderByCreatedAtDesc(courseSpaceCard.getId(), userId);
        if (history.isPresent()) {
            CardMembership priorEpisode = history.get();
            if (priorEpisode.getRole() == MembershipRole.OWNER
                    && priorEpisode.getMemberCardId().equals(courseSpaceCard.getId())) {
                throw new ConflictException("Owner member-Card relationship requires transfer reconciliation");
            }
            return cardRepository.findByIdAndOwnerId(priorEpisode.getMemberCardId(), userId)
                    .orElseThrow(() -> new ConflictException("Historical member Card is missing or has a different owner"));
        }
        return cardRepository.save(new Card(userId, courseSpaceCard.getName(), courseSpaceCard.getColor()));
    }

    private Card lockCard(UUID cardId) {
        return cardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
    }

    private Card requireSharedCard(UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        requireShared(card);
        return card;
    }

    private void requireOwner(Card card, UUID userId) {
        if (!card.isOwnedBy(userId)) throw new ForbiddenException("Only the Card Owner may perform this action");
        requireUser(userId);
    }

    private void requireOwnerOrAdmin(Card card, UUID userId) {
        requireShared(card);
        if (card.isOwnedBy(userId)) {
            requireUser(userId);
            return;
        }
        CardMembership membership = membershipRepository.findFirstByCardIdAndUserIdAndStatusIn(
                card.getId(), userId, List.of(MembershipStatus.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (membership.getRole() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Owner or active Admin role is required");
        }
    }

    private void requireActiveMember(Card card, UUID userId) {
        requireShared(card);
        if (card.isOwnedBy(userId)) {
            requireUser(userId);
            return;
        }
        if (!membershipRepository.existsByCardIdAndUserIdAndStatus(card.getId(), userId, MembershipStatus.ACTIVE)) {
            throw new ResourceNotFoundException("Card not found");
        }
    }

    private void requireShared(Card card) {
        if (!card.isShared()) throw new ResourceNotFoundException("Course Space not found");
    }

    private void requireUser(UUID userId) {
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User not found");
    }

    private void invalidatePendingRequests(UUID cardId, long inviteVersion, UUID actorId) {
        if (inviteVersion == 0) return;
        List<CardJoinRequest> pending = joinRequestRepository.findByCardIdAndInviteTokenVersionAndStatus(
                cardId, inviteVersion, JoinRequestStatus.PENDING);
        pending.forEach(request -> request.resolve(JoinRequestStatus.INVALIDATED, actorId));
    }

    private void requireOwnedInvitation(CardMembership membership, UUID userId) {
        if (!membership.getUserId().equals(userId) || membership.getStatus() != MembershipStatus.INVITED) {
            throw new ResourceNotFoundException("Invitation not found");
        }
    }

    private ShareLinkResponse shareLinkResponse(Card card) {
        return new ShareLinkResponse(card.getInviteToken(), inviteBaseUrl + "/join/" + card.getInviteToken(),
                card.isRequireApproval());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void emit(UUID cardId, String type, UUID actorId, Map<String, ?> payload) {
        eventService.emit(cardId, type, actorId, payload);
    }

}
