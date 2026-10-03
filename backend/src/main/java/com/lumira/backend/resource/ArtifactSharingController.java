package com.lumira.backend.resource;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequireAuth
public class ArtifactSharingController {
    private final ResourceSharingService sharing;

    public ArtifactSharingController(ResourceSharingService sharing) {
        this.sharing = sharing;
    }

    @PostMapping("/v1/artifacts/{artifactType}/{artifactId}/share")
    public ResponseEntity<ResourceShareResponse> share(@PathVariable String artifactType,
            @PathVariable UUID artifactId, @Valid @RequestBody ShareResourceRequest request,
            @CurrentUser AuthenticatedUser user) {
        ArtifactType type = ArtifactType.fromApiValue(artifactType);
        ResourceSharingService.ShareResult result = sharing.share(type, artifactId, request.cardId(), user.userId());
        return ResponseEntity.status(result.changed() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ResourceShareResponse.from(result.share()));
    }

    @DeleteMapping("/v1/artifacts/{artifactType}/{artifactId}/share/{cardId}")
    public Object unshare(@PathVariable String artifactType, @PathVariable UUID artifactId,
            @PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        ArtifactType type = ArtifactType.fromApiValue(artifactType);
        return sharing.unshare(type, artifactId, cardId, user.userId());
    }

    @PostMapping("/v1/artifacts/{artifactType}/{artifactId}/force-unshare/{cardId}")
    public Object forceUnshare(@PathVariable String artifactType, @PathVariable UUID artifactId,
            @PathVariable UUID cardId, @RequestBody(required = false) ForceUnshareRequest request,
            @CurrentUser AuthenticatedUser user) {
        ArtifactType type = ArtifactType.fromApiValue(artifactType);
        String reason = request == null ? null : request.reason();
        String note = request == null ? null : request.note();
        return sharing.forceUnshare(type, artifactId, cardId, user.userId(), reason, note);
    }
}
