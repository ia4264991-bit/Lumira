package com.aipdfreader.app.data.remote

import com.aipdfreader.app.data.remote.dto.AuthResponse
import com.aipdfreader.app.data.remote.dto.LoginRequest
import com.aipdfreader.app.data.remote.dto.RefreshRequest
import com.aipdfreader.app.data.remote.dto.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

/** Our backend's Authentication component. No third-party auth here. */
interface AuthApi {

    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse
}
