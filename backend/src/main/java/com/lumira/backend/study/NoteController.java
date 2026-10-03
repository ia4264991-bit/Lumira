package com.lumira.backend.study;

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
public class NoteController {
    private final NoteService service;
    public NoteController(NoteService service) { this.service = service; }

    @PostMapping("/v1/cards/{cardId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@PathVariable UUID cardId, @Valid @RequestBody NoteCreateRequest request,
            @CurrentUser AuthenticatedUser user) { return service.create(cardId, user.userId(), request); }

    @GetMapping("/v1/cards/{cardId}/notes")
    public List<NoteResponse> list(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return service.list(cardId, user.userId());
    }

    @GetMapping("/v1/notes/{noteId}")
    public NoteResponse get(@PathVariable UUID noteId, @CurrentUser AuthenticatedUser user) {
        return service.get(noteId, user.userId());
    }

    @PatchMapping("/v1/notes/{noteId}")
    public NoteResponse update(@PathVariable UUID noteId, @RequestBody NotePatchRequest request,
            @CurrentUser AuthenticatedUser user) { return service.update(noteId, user.userId(), request); }

    @DeleteMapping("/v1/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID noteId, @CurrentUser AuthenticatedUser user) {
        service.delete(noteId, user.userId());
    }
}
