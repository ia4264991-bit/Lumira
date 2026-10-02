package com.lumira.backend.card;

public record ShareLinkResponse(String shareToken, String url, boolean requireApproval) {}
