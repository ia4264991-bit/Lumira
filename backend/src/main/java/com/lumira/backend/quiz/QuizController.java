package com.lumira.backend.quiz;

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
public class QuizController {
    private final QuizService service;

    public QuizController(QuizService service) { this.service = service; }

    @PostMapping("/v1/cards/{cardId}/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizResponse create(@PathVariable UUID cardId, @Valid @RequestBody QuizCreateRequest request,
            @CurrentUser AuthenticatedUser user) {
        return service.create(cardId, user.userId(), request);
    }

    @GetMapping("/v1/cards/{cardId}/quizzes")
    public List<QuizResponse> list(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return service.list(cardId, user.userId());
    }

    @GetMapping("/v1/quizzes/{quizId}")
    public QuizResponse get(@PathVariable UUID quizId, @CurrentUser AuthenticatedUser user) {
        return service.get(quizId, user.userId());
    }

    @PatchMapping("/v1/quizzes/{quizId}")
    public QuizResponse update(@PathVariable UUID quizId, @Valid @RequestBody QuizPatchRequest request,
            @CurrentUser AuthenticatedUser user) {
        return service.update(quizId, user.userId(), request);
    }

    @DeleteMapping("/v1/quizzes/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID quizId, @CurrentUser AuthenticatedUser user) {
        service.delete(quizId, user.userId());
    }

    @PostMapping("/v1/quizzes/{quizId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizAttemptResponse submit(@PathVariable UUID quizId, @Valid @RequestBody QuizAttemptRequest request,
            @CurrentUser AuthenticatedUser user) {
        return service.submit(quizId, user.userId(), request);
    }

    @GetMapping("/v1/quizzes/{quizId}/attempts/me")
    public List<QuizAttemptResponse> myAttempts(@PathVariable UUID quizId, @CurrentUser AuthenticatedUser user) {
        return service.myAttempts(quizId, user.userId());
    }
}
