package com.aipdfreader.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTOs for OUR backend's `/v1/auth/*` endpoints. These describe our own
 * contract only — nothing here is provider-specific (no OpenAI/Anthropic/etc.
 * shapes leak into the client).
 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val email: String? = null
)

@Serializable
data class ApiErrorBody(
    val message: String? = null
)
