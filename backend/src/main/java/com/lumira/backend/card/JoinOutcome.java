package com.lumira.backend.card;

public record JoinOutcome(Card card, CardJoinRequest request) {
    public boolean awaitingApproval() { return request != null; }
}
