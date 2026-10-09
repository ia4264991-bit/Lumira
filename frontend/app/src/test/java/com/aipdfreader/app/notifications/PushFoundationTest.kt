package com.aipdfreader.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushFoundationTest {
    @Test
    fun validSignalUsesOnlyExistingEventIdentityFields() {
        val signal = PushSignalParser.parse(
            mapOf("eventId" to "6f9619ff-8b86-d011-b42d-00cf4fc964ff", "type" to "RESOURCE_ADDED", "body" to "private")
        )

        assertEquals("6f9619ff-8b86-d011-b42d-00cf4fc964ff", signal?.eventId)
        assertEquals("RESOURCE_ADDED", signal?.type)
    }

    @Test
    fun malformedOrIncompleteSignalIsIgnored() {
        assertNull(PushSignalParser.parse(mapOf("eventId" to "bad", "type" to "RESOURCE_ADDED")))
        assertNull(PushSignalParser.parse(mapOf("eventId" to "6f9619ff-8b86-d011-b42d-00cf4fc964ff")))
    }

    @Test
    fun installationRefreshIsRetainedOnlyForAnAuthenticatedSession() {
        assertEquals("refreshed-fid", PushInstallationPolicy.currentToken(true, "refreshed-fid"))
        assertNull(PushInstallationPolicy.currentToken(false, "refreshed-fid"))
        assertNull(PushInstallationPolicy.currentToken(true, " "))
    }

    @Test
    fun notificationPermissionIsRequiredStartingAtAndroid13() {
        assertFalse(PushPermissionPolicy.requiresRuntimePermission(32))
        assertTrue(PushPermissionPolicy.requiresRuntimePermission(33))
    }
}
