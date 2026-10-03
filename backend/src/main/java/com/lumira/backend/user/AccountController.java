package com.lumira.backend.user;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequireAuth
public class AccountController {
    private final AccountDeletionService deletionService;

    public AccountController(AccountDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    @DeleteMapping("/v1/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@CurrentUser AuthenticatedUser user) {
        deletionService.deleteAccount(user.userId());
    }
}
