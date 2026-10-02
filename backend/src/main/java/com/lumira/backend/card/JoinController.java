package com.lumira.backend.card;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/join")
@RequireAuth
public class JoinController {
    private final CourseSpaceService courseSpaceService;
    public JoinController(CourseSpaceService courseSpaceService) { this.courseSpaceService = courseSpaceService; }

    @PostMapping("/{shareToken}")
    public ResponseEntity<?> join(@PathVariable String shareToken, @CurrentUser AuthenticatedUser user) {
        JoinOutcome outcome = courseSpaceService.joinByLink(shareToken, user.userId());
        if (outcome.awaitingApproval()) {
            CardJoinRequest request = outcome.request();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new JoinRequestCreatedResponse(
                    request.getId(), request.getStatus().name()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(outcome.card()));
    }
}
