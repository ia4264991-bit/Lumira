package com.lumira.backend.card;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/me/invitations")
@RequireAuth
public class InvitationController {
    private final CourseSpaceService courseSpaceService;
    public InvitationController(CourseSpaceService courseSpaceService) { this.courseSpaceService = courseSpaceService; }

    @GetMapping
    public List<DirectInvitationResponse> list(@CurrentUser AuthenticatedUser user) {
        return courseSpaceService.listMyInvitations(user.userId());
    }

    @GetMapping("/{membershipId}")
    public DirectInvitationResponse get(@PathVariable UUID membershipId, @CurrentUser AuthenticatedUser user) {
        return DirectInvitationResponse.from(courseSpaceService.getMyInvitation(membershipId, user.userId()));
    }

    @PostMapping("/{membershipId}/accept")
    public ResponseEntity<DirectInvitationResponse> accept(@PathVariable UUID membershipId,
                                                            @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(DirectInvitationResponse.from(
                courseSpaceService.acceptInvitation(membershipId, user.userId())));
    }

    @PostMapping("/{membershipId}/decline")
    public ResponseEntity<DirectInvitationResponse> decline(@PathVariable UUID membershipId,
                                                             @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(DirectInvitationResponse.from(
                courseSpaceService.declineInvitation(membershipId, user.userId())));
    }
}
