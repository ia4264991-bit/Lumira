package com.lumira.backend.flashcard;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequireAuth
public class FlashcardSetController {
    private final FlashcardSetService service;
    public FlashcardSetController(FlashcardSetService service) { this.service = service; }

    @PostMapping("/v1/cards/{cardId}/flashcard-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public FlashcardSetResponse create(@PathVariable UUID cardId, @Valid @RequestBody FlashcardSetCreateRequest request,
            @CurrentUser AuthenticatedUser user) { return service.create(cardId, user.userId(), request); }

    @GetMapping("/v1/cards/{cardId}/flashcard-sets")
    public List<FlashcardSetResponse> list(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return service.list(cardId, user.userId());
    }

    @GetMapping("/v1/flashcard-sets/{setId}")
    public FlashcardSetResponse get(@PathVariable UUID setId, @CurrentUser AuthenticatedUser user) {
        return service.get(setId, user.userId());
    }

    @PatchMapping("/v1/flashcard-sets/{setId}")
    public FlashcardSetResponse update(@PathVariable UUID setId, @Valid @RequestBody FlashcardSetPatchRequest request,
            @CurrentUser AuthenticatedUser user) { return service.update(setId, user.userId(), request); }

    @DeleteMapping("/v1/flashcard-sets/{setId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID setId, @CurrentUser AuthenticatedUser user) { service.delete(setId, user.userId()); }

    @PostMapping("/v1/flashcard-sets/{setId}/progress")
    @ResponseStatus(HttpStatus.CREATED)
    public FlashcardProgressResponse record(@PathVariable UUID setId, @Valid @RequestBody FlashcardProgressRequest request,
            @CurrentUser AuthenticatedUser user) { return service.recordProgress(setId, user.userId(), request); }

    @GetMapping("/v1/flashcard-sets/{setId}/progress/me")
    public List<FlashcardProgressResponse> myProgress(@PathVariable UUID setId, @CurrentUser AuthenticatedUser user) {
        return service.myProgress(setId, user.userId());
    }
}
