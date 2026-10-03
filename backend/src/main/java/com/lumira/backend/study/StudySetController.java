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
public class StudySetController {
    private final StudySetService service;
    public StudySetController(StudySetService service) { this.service = service; }

    @PostMapping("/v1/cards/{cardId}/studysets")
    @ResponseStatus(HttpStatus.CREATED)
    public StudySetResponse create(@PathVariable UUID cardId, @Valid @RequestBody StudySetCreateRequest request,
            @CurrentUser AuthenticatedUser user) { return service.create(cardId, user.userId(), request); }

    @GetMapping("/v1/cards/{cardId}/studysets")
    public List<StudySetResponse> list(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return service.list(cardId, user.userId());
    }

    @GetMapping("/v1/studysets/{studySetId}")
    public StudySetResponse get(@PathVariable UUID studySetId, @CurrentUser AuthenticatedUser user) {
        return service.get(studySetId, user.userId());
    }

    @PatchMapping("/v1/studysets/{studySetId}")
    public StudySetResponse update(@PathVariable UUID studySetId, @RequestBody StudySetPatchRequest request,
            @CurrentUser AuthenticatedUser user) { return service.update(studySetId, user.userId(), request); }

    @DeleteMapping("/v1/studysets/{studySetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID studySetId, @CurrentUser AuthenticatedUser user) {
        service.delete(studySetId, user.userId());
    }
}
