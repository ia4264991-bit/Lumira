package com.aipdfreader.app.notifications

import java.util.UUID

/** Minimal, non-content push envelope derived from the existing notification event model. */
data class PushSignal(val eventId: String, val type: String)

object PushSignalParser {
    fun parse(data: Map<String, String>): PushSignal? {
        val eventId = data["eventId"]?.takeIf { value ->
            runCatching { UUID.fromString(value) }.isSuccess
        } ?: return null
        val type = data["type"]?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return PushSignal(eventId, type)
    }
}

object PushInstallationPolicy {
    fun currentToken(isLoggedIn: Boolean, token: String?): String? =
        token?.takeIf { isLoggedIn && it.isNotBlank() }
}

object PushPermissionPolicy {
    fun requiresRuntimePermission(apiLevel: Int): Boolean = apiLevel >= 33
}
