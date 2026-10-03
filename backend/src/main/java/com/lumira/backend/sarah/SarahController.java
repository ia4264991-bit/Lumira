package com.lumira.backend.sarah;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequireAuth
public class SarahController {
    private final SarahService service;

    public SarahController(SarahService service) { this.service = service; }

    @PostMapping("/cards/{cardId}/sarah/ask")
    public SarahAskResponse askWorkspace(@PathVariable UUID cardId,
            @Valid @RequestBody WorkspaceAskRequest request, @CurrentUser AuthenticatedUser user) {
        return service.askWorkspace(cardId, user.userId(), request);
    }

    @PostMapping("/resources/{resourceId}/sarah/ask")
    public SarahAskResponse askContextual(@PathVariable UUID resourceId,
            @Valid @RequestBody ContextualAskRequest request, @CurrentUser AuthenticatedUser user) {
        return service.askContextual(resourceId, user.userId(), request);
    }

    @PostMapping("/cards/{cardId}/sarah/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public SarahGeneratedArtifact generate(@PathVariable UUID cardId,
            @Valid @RequestBody SarahGenerationRequest request, @CurrentUser AuthenticatedUser user) {
        return service.generate(cardId, user.userId(), request);
    }
}
