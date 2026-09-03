package com.aipdfreader.app.data.remote

import com.aipdfreader.app.data.remote.dto.AskRequest
import com.aipdfreader.app.data.remote.dto.AskResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Our backend's AI Router component. This is the ONLY way the Android app
 * reaches an AI capability — there is no direct provider communication, no
 * provider selection, and no prompt construction on-device. The Authorization
 * header (our own session token, not an AI key) is attached automatically by
 * [AuthInterceptor].
 */
interface AiRouterApi {

    @POST("v1/ai/ask")
    suspend fun ask(@Body request: AskRequest): AskResponse
}
