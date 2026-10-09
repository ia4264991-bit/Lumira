package com.aipdfreader.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CardDto(
    val id: String,
    val ownerId: String,
    val name: String,
    val color: String = "#6687E8",
    val isShared: Boolean = false,
    val role: String? = null,
    val createdAt: String? = null,
    val memberCardId: String? = null,
    val shareApproval: Boolean? = null
)

@Serializable
data class CreateCardRequest(val name: String, val color: String)

@Serializable
data class RenameCardRequest(val name: String)

@Serializable
data class ShareLinkDto(val shareToken: String, val url: String, val requireApproval: Boolean)

@Serializable
data class RequireApprovalRequest(val requireApproval: Boolean)

@Serializable
data class JoinRequestDto(
    val id: String? = null,
    val joinRequestId: String? = null,
    val status: String? = null,
    val name: String? = null
)
