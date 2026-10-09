package com.aipdfreader.app.ui.home

import com.aipdfreader.app.data.local.entity.LocalCardEntity
import com.aipdfreader.app.data.remote.dto.CardDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CardHomeProjectionTest {
    @Test
    fun privateCardsHideServerMirrorsAndCourseSpaceMemberCopies() {
        val privateCard = card("server-private")
        val mirroredPrivateCard = card("server-mirror")
        val sharedCard = card("shared-space", shared = true, memberCardId = "member-copy")
        val localCards = listOf(
            local("local-mirror", remoteId = "server-mirror"),
            local("member-copy-local", remoteId = "member-copy"),
            local("device-only")
        )

        val result = projectHomeCards(
            personalCards = listOf(privateCard, mirroredPrivateCard),
            sharedCards = listOf(sharedCard),
            localCards = localCards,
            showingCourseSpaces = false
        )

        assertEquals(listOf("server-private", "local:local-mirror", "local:device-only"), result.map { it.id })
    }

    @Test
    fun courseSpaceTabContainsOnlyServerAuthorizedSharedCards() {
        val shared = card("shared-space", shared = true)

        val result = projectHomeCards(
            personalCards = listOf(card("private")),
            sharedCards = listOf(shared),
            localCards = listOf(local("offline")),
            showingCourseSpaces = true
        )

        assertEquals(listOf(shared), result)
    }

    @Test
    fun deletedLocalCardAndItsServerMirrorAreHiddenDuringPendingSync() {
        val result = projectHomeCards(
            personalCards = listOf(card("server-copy")),
            sharedCards = emptyList(),
            localCards = listOf(local("pending-delete", remoteId = "server-copy").copy(isDeleted = true)),
            showingCourseSpaces = false
        )

        assertEquals(emptyList<CardDto>(), result)
    }

    private fun card(id: String, shared: Boolean = false, memberCardId: String? = null) = CardDto(
        id = id,
        ownerId = "owner",
        name = id,
        isShared = shared,
        role = if (shared) "MEMBER" else "OWNER",
        memberCardId = memberCardId
    )

    private fun local(id: String, remoteId: String? = null) = LocalCardEntity(
        id = id,
        ownerUid = "account",
        name = id,
        color = "#6687E8",
        createdAtMillis = 1L,
        remoteCardId = remoteId
    )
}
