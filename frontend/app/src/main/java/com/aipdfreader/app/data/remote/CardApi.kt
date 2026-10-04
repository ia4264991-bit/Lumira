package com.aipdfreader.app.data.remote

import com.aipdfreader.app.data.remote.dto.CardDto
import com.aipdfreader.app.data.remote.dto.CreateCardRequest
import com.aipdfreader.app.data.remote.dto.JoinRequestDto
import com.aipdfreader.app.data.remote.dto.ShareLinkDto
import com.aipdfreader.app.data.remote.dto.RequireApprovalRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Response
import okhttp3.ResponseBody

interface CardApi {
    @GET("v1/cards")
    suspend fun listCards(@Query("scope") scope: String? = null): List<CardDto>
    @GET("v1/cards/{cardId}")
    suspend fun getCard(@Path("cardId") cardId: String): CardDto
    @POST("v1/cards")
    suspend fun createCard(@Body request: CreateCardRequest): CardDto
    @POST("v1/cards/{cardId}/share")
    suspend fun enableSharing(@Path("cardId") cardId: String): CardDto
    @POST("v1/cards/{cardId}/share-link")
    suspend fun createShareLink(@Path("cardId") cardId: String): ShareLinkDto
    @GET("v1/cards/{cardId}/share-link")
    suspend fun getShareLink(@Path("cardId") cardId: String): ShareLinkDto
    @PATCH("v1/cards/{cardId}/share-link/approval")
    suspend fun setJoinApproval(@Path("cardId") cardId: String, @Body request: RequireApprovalRequest): CardDto
    @POST("v1/join/{shareToken}")
    suspend fun joinByLink(@Path("shareToken") shareToken: String): Response<ResponseBody>
}
