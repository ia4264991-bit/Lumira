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
        requireResourceType(artifactType);
        ResourceSharingService.ShareResult result = sharing.share(artifactId, request.cardId(), user.userId());
        return ResponseEntity.status(result.changed() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ResourceShareResponse.from(result.share()));
    }

    @DeleteMapping("/v1/artifacts/{artifactType}/{artifactId}/share/{cardId}")
    public ResourceResponse unshare(@PathVariable String artifactType, @PathVariable UUID artifactId,
            @PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        requireResourceType(artifactType);
        return ResourceResponse.from(sharing.unshare(artifactId, cardId, user.userId()));
    }

    @PostMapping("/v1/artifacts/{artifactType}/{artifactId}/force-unshare/{cardId}")
    public ResourceResponse forceUnshare(@PathVariable String artifactType, @PathVariable UUID artifactId,
            @PathVariable UUID cardId, @RequestBody(required = false) ForceUnshareRequest request,
            @CurrentUser AuthenticatedUser user) {
        requireResourceType(artifactType);
        String reason = request == null ? null : request.reason();
        String note = request == null ? null : request.note();
        return ResourceResponse.from(sharing.forceUnshare(artifactId, cardId, user.userId(), reason, note));
    }

    private void requireResourceType(String artifactType) {
        if (!"resource".equals(artifactType)) {
            throw new com.lumira.backend.common.error.ResourceNotFoundException("Artifact not found");
        }
    }
}
