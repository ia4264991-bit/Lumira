package com.aipdfreader.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class ResourceDto(val id: String, val title: String, val originalFilename: String? = null, val mimeType: String? = null, val status: String = "READY", val fileSizeBytes: Long = 0, val extractedContent: JsonElement? = null, val createdAt: String? = null)
@Serializable data class NoteDto(val id: String, val title: String, val content: String, val updatedAt: String? = null,
    val ownerCardId: String? = null, val ownerUserId: String? = null, val sharedWithThisCourseSpace: Boolean = false)
@Serializable data class NoteWriteDto(val title: String, val content: String)
@Serializable data class StudySetDto(val id: String, val title: String, val description: String = "", val updatedAt: String? = null,
    val ownerCardId: String? = null, val ownerUserId: String? = null, val sharedWithThisCourseSpace: Boolean = false)
@Serializable data class StudySetWriteDto(val title: String, val description: String)
@Serializable data class QuizOptionDto(val id: String, val position: Int, val text: String, val correct: Boolean? = null)
@Serializable data class QuizQuestionDto(val id: String, val position: Int, val prompt: String, val options: List<QuizOptionDto>)
@Serializable data class QuizDto(val id: String, val title: String, val description: String = "", val questions: List<QuizQuestionDto> = emptyList(),
    val ownerCardId: String? = null, val ownerUserId: String? = null, val sharedWithThisCourseSpace: Boolean = false)
@Serializable data class QuizOptionWriteDto(val position: Int, val text: String, val correct: Boolean)
@Serializable data class QuizQuestionWriteDto(val position: Int, val prompt: String, val options: List<QuizOptionWriteDto>)
@Serializable data class QuizWriteDto(val title: String, val description: String, val questions: List<QuizQuestionWriteDto>)
@Serializable data class QuizAnswerWriteDto(val questionId: String, val optionId: String)
@Serializable data class QuizAttemptWriteDto(val answers: List<QuizAnswerWriteDto>)
@Serializable data class QuizAttemptDto(val id: String, val correctCount: Int, val totalQuestions: Int, val createdAt: String? = null)
@Serializable data class FlashcardDto(val id: String, val position: Int, val front: String, val back: String)
@Serializable data class FlashcardSetDto(val id: String, val title: String, val description: String = "", val cards: List<FlashcardDto> = emptyList(),
    val ownerCardId: String? = null, val ownerUserId: String? = null, val sharedWithThisCourseSpace: Boolean = false)
@Serializable data class FlashcardWriteDto(val position: Int, val front: String, val back: String)
@Serializable data class FlashcardSetWriteDto(val title: String, val description: String, val cards: List<FlashcardWriteDto>)
@Serializable data class FlashcardProgressWriteDto(val cardId: String, val outcome: String)
@Serializable data class FlashcardProgressDto(val flashcardId: String, val outcome: String, val reviewedAt: String? = null)
@Serializable data class EventDto(val id: String, val type: String, val createdAt: String, val payload: JsonElement? = null)
@Serializable data class NotificationDto(val id: String, val eventId: String, val cardId: String? = null, val type: String, val createdAt: String, val readAt: String? = null, val payload: JsonElement? = null)
@Serializable data class MemberDto(val userId: String, val status: String, val role: String, val joinedAt: String? = null, val membershipId: String = "")
@Serializable data class JoinRequestItemDto(val joinRequestId: String, val requestingUserId: String, val status: String, val createdAt: String? = null)
@Serializable data class JoinRequestStatusDto(val status: String)
@Serializable data class InviteUserDto(val userId: String)
@Serializable data class DirectInvitationDto(val membershipId: String, val userId: String, val status: String, val role: String, val cardId: String = "", val memberCardId: String = "", val createdAt: String? = null)
@Serializable data class TransferOwnershipDto(val targetUserId: String)
@Serializable data class ArtifactShareDto(val cardId: String)
@Serializable data class SarahHistoryItem(val role: String, val content: String)
@Serializable data class SarahAskDto(val conversationId: String, val question: String, val conversationHistory: List<SarahHistoryItem> = emptyList())
@Serializable data class SarahResourceAskDto(val conversationId: String, val cardId: String, val selectedText: String?, val question: String, val conversationHistory: List<SarahHistoryItem> = emptyList(), val pageIndex: Int?)
@Serializable data class SarahAnswerDto(val answer: String, val conversationId: String, val usageUsed: Int, val usageLimit: Int)
@Serializable data class SarahGenerateDto(val artifactType: String, val sourceResourceIds: List<String>, val instructions: String? = null)
@Serializable data class SarahGenerationDto(val artifactType: String, val artifact: JsonElement, val usageUsed: Int, val usageLimit: Int)
