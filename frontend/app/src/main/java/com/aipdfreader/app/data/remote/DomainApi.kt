package com.aipdfreader.app.data.remote

import com.aipdfreader.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** Thin-client endpoints from API_CONTRACT.md. Authorization stays on the server. */
interface DomainApi {
    @GET("v1/cards/{cardId}/resources") suspend fun resources(@Path("cardId") cardId: String): List<ResourceDto>
    @Multipart @POST("v1/cards/{cardId}/resources") suspend fun uploadResource(
        @Path("cardId") cardId: String,
        @Part file: MultipartBody.Part,
        @Query("title") title: String
    ): ResourceDto
    @GET("v1/resources/{resourceId}") suspend fun downloadResource(@Path("resourceId") id: String): ResponseBody

    @GET("v1/cards/{cardId}/notes") suspend fun notes(@Path("cardId") cardId: String): List<NoteDto>
    @POST("v1/cards/{cardId}/notes") suspend fun createNote(@Path("cardId") cardId: String, @Body body: NoteWriteDto): NoteDto
    @PATCH("v1/notes/{noteId}") suspend fun updateNote(@Path("noteId") id: String, @Body body: NoteWriteDto): NoteDto
    @DELETE("v1/notes/{noteId}") suspend fun deleteNote(@Path("noteId") id: String): Response<Unit>

    @GET("v1/cards/{cardId}/studysets") suspend fun studySets(@Path("cardId") cardId: String): List<StudySetDto>
    @POST("v1/cards/{cardId}/studysets") suspend fun createStudySet(@Path("cardId") cardId: String, @Body body: StudySetWriteDto): StudySetDto
    @PATCH("v1/studysets/{studySetId}") suspend fun updateStudySet(@Path("studySetId") id: String, @Body body: StudySetWriteDto): StudySetDto
    @DELETE("v1/studysets/{studySetId}") suspend fun deleteStudySet(@Path("studySetId") id: String): Response<Unit>

    @GET("v1/cards/{cardId}/quizzes") suspend fun quizzes(@Path("cardId") cardId: String): List<QuizDto>
    @POST("v1/cards/{cardId}/quizzes") suspend fun createQuiz(@Path("cardId") cardId: String, @Body body: QuizWriteDto): QuizDto
    @DELETE("v1/quizzes/{quizId}") suspend fun deleteQuiz(@Path("quizId") id: String): Response<Unit>
    @POST("v1/quizzes/{quizId}/attempts") suspend fun submitQuizAttempt(@Path("quizId") id: String, @Body body: QuizAttemptWriteDto): QuizAttemptDto
    @GET("v1/quizzes/{quizId}/attempts/me") suspend fun myQuizAttempts(@Path("quizId") id: String): List<QuizAttemptDto>

    @GET("v1/cards/{cardId}/flashcard-sets") suspend fun flashcardSets(@Path("cardId") cardId: String): List<FlashcardSetDto>
    @POST("v1/cards/{cardId}/flashcard-sets") suspend fun createFlashcardSet(@Path("cardId") cardId: String, @Body body: FlashcardSetWriteDto): FlashcardSetDto
    @DELETE("v1/flashcard-sets/{setId}") suspend fun deleteFlashcardSet(@Path("setId") id: String): Response<Unit>
    @POST("v1/flashcard-sets/{setId}/progress") suspend fun reviewFlashcard(@Path("setId") id: String, @Body body: FlashcardProgressWriteDto): FlashcardProgressDto

    @GET("v1/cards/{cardId}/events") suspend fun events(@Path("cardId") cardId: String): List<EventDto>
    @GET("v1/notifications") suspend fun notifications(): List<NotificationDto>
    @PATCH("v1/notifications/{id}/read") suspend fun markNotificationRead(@Path("id") id: String): NotificationDto

    @POST("v1/cards/{cardId}/sarah/ask") suspend fun askSarah(@Path("cardId") cardId: String, @Body body: SarahAskDto): SarahAnswerDto
    @POST("v1/resources/{resourceId}/sarah/ask") suspend fun askSarahAboutResource(
        @Path("resourceId") resourceId: String,
        @Body body: SarahResourceAskDto
    ): SarahAnswerDto
    @POST("v1/cards/{cardId}/sarah/generate") suspend fun generateWithSarah(@Path("cardId") cardId: String, @Body body: SarahGenerateDto): SarahGenerationDto

    @GET("v1/cards/{cardId}/members") suspend fun members(@Path("cardId") cardId: String): List<MemberDto>
    @GET("v1/cards/{cardId}/join-requests") suspend fun joinRequests(@Path("cardId") cardId: String): List<JoinRequestItemDto>
    @POST("v1/cards/{cardId}/join-requests/{requestId}/approve") suspend fun approveJoin(@Path("cardId") cardId: String, @Path("requestId") requestId: String): CardDto
    @POST("v1/cards/{cardId}/join-requests/{requestId}/reject") suspend fun rejectJoin(@Path("cardId") cardId: String, @Path("requestId") requestId: String): JoinRequestStatusDto
    @POST("v1/cards/{cardId}/invitations") suspend fun invite(@Path("cardId") cardId: String, @Body body: InviteUserDto): DirectInvitationDto
    @POST("v1/cards/{cardId}/members/{userId}/promote") suspend fun promote(@Path("cardId") cardId: String, @Path("userId") userId: String): MemberDto
    @POST("v1/cards/{cardId}/members/{userId}/demote") suspend fun demote(@Path("cardId") cardId: String, @Path("userId") userId: String): MemberDto
    @DELETE("v1/cards/{cardId}/members/{userId}") suspend fun removeMember(@Path("cardId") cardId: String, @Path("userId") userId: String): MemberDto
    @POST("v1/cards/{cardId}/transfer-ownership") suspend fun transferOwnership(@Path("cardId") cardId: String, @Body body: TransferOwnershipDto): MemberDto
    @POST("v1/cards/{cardId}/leave") suspend fun leaveCourseSpace(@Path("cardId") cardId: String): MemberDto
    @DELETE("v1/cards/{cardId}/share") suspend fun dissolveCourseSpace(@Path("cardId") cardId: String): CardDto
}
