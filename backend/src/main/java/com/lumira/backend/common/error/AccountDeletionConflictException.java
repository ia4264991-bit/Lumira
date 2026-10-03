package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

public class AccountDeletionConflictException extends LumiraException {
    private final List<UUID> courseSpaceCardIds;

    public AccountDeletionConflictException(List<UUID> cardIds) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                "Transfer ownership or dissolve all Course Spaces before deleting this account");
        this.courseSpaceCardIds = List.copyOf(cardIds);
    }

    public List<UUID> getCourseSpaceCardIds() { return courseSpaceCardIds; }
}
