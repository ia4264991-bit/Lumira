package com.lumira.backend.card;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/cards/{cardId}")
@RequireAuth
public class CourseSpaceController {
    private final CourseSpaceService courseSpaceService;
    public CourseSpaceController(CourseSpaceService courseSpaceService) { this.courseSpaceService = courseSpaceService; }

    @PostMapping("/share")
    public CardResponse enableSharing(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return CardResponse.from(courseSpaceService.enableSharing(cardId, user.userId()));
    }

    @PostMapping("/share-link")
    public ShareLinkResponse resetLink(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return courseSpaceService.resetShareLink(cardId, user.userId());
    }

    @GetMapping("/share-link")
    public ShareLinkResponse getLink(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return courseSpaceService.getShareLink(cardId, user.userId());
    }

    @PatchMapping("/share-link/approval")
    public CardResponse setApproval(@PathVariable UUID cardId, @Valid @RequestBody RequireApprovalRequest request,
                                    @CurrentUser AuthenticatedUser user) {
        return CardResponse.from(courseSpaceService.setRequireApproval(cardId, user.userId(), request.requireApproval()));
    }

    @PostMapping("/invitations")
    public ResponseEntity<DirectInvitationResponse> invite(@PathVariable UUID cardId,
            @Valid @RequestBody InviteUserRequest request, @CurrentUser AuthenticatedUser user) {
        InviteOutcome outcome = courseSpaceService.inviteUser(cardId, user.userId(), request.userId());
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(DirectInvitationResponse.from(outcome.membership()));
    }

    @GetMapping("/members")
    public List<CardMembershipResponse> members(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return courseSpaceService.listMembers(cardId, user.userId());
    }

    @PostMapping("/members/{userId}/promote")
    public CardMembershipResponse promote(@PathVariable UUID cardId, @PathVariable UUID userId,
                                          @CurrentUser AuthenticatedUser user) {
        return CardMembershipResponse.from(courseSpaceService.promote(cardId, userId, user.userId()));
    }

    @PostMapping("/members/{userId}/demote")
    public CardMembershipResponse demote(@PathVariable UUID cardId, @PathVariable UUID userId,
                                          @CurrentUser AuthenticatedUser user) {
        return CardMembershipResponse.from(courseSpaceService.demote(cardId, userId, user.userId()));
    }

    @DeleteMapping("/members/{userId}")
    public CardMembershipResponse remove(@PathVariable UUID cardId, @PathVariable UUID userId,
                                          @CurrentUser AuthenticatedUser user) {
        return CardMembershipResponse.from(courseSpaceService.removeMember(cardId, userId, user.userId()));
    }

    @DeleteMapping("/invitations/{membershipId}")
    public ResponseEntity<DirectInvitationResponse> withdraw(@PathVariable UUID cardId,
            @PathVariable UUID membershipId, @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(DirectInvitationResponse.from(
                courseSpaceService.withdrawInvitation(cardId, membershipId, user.userId())));
    }

    @PostMapping("/leave")
    public CardMembershipResponse leave(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return CardMembershipResponse.from(courseSpaceService.leave(cardId, user.userId()));
    }

    @DeleteMapping("/share")
    public CardResponse dissolve(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return CardResponse.from(courseSpaceService.dissolve(cardId, user.userId()));
    }

    @GetMapping("/events")
    public List<CourseSpaceEventResponse> events(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return courseSpaceService.listEvents(cardId, user.userId());
    }

    @GetMapping("/join-requests")
    public List<JoinRequestResponse> joinRequests(@PathVariable UUID cardId,
                                                   @CurrentUser AuthenticatedUser user) {
        return courseSpaceService.listJoinRequests(cardId, user.userId());
    }

    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<CardResponse> approve(@PathVariable UUID cardId, @PathVariable UUID requestId,
                                                @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                CardResponse.from(courseSpaceService.approveJoinRequest(cardId, requestId, user.userId())));
    }

    @PostMapping("/join-requests/{requestId}/reject")
    public JoinRequestStatusResponse reject(@PathVariable UUID cardId, @PathVariable UUID requestId,
                                            @CurrentUser AuthenticatedUser user) {
        return new JoinRequestStatusResponse(courseSpaceService.rejectJoinRequest(
                cardId, requestId, user.userId()).getStatus().name());
    }
}
