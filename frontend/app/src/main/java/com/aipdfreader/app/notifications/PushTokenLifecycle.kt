package com.aipdfreader.app.notifications

import com.aipdfreader.app.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Holds the current FCM installation ID only in memory; backend registration awaits an API contract. */
@Singleton
class PushTokenLifecycle @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val authRepository: AuthRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentInstallationId = MutableStateFlow<String?>(null)
    val currentInstallationId: StateFlow<String?> = _currentInstallationId.asStateFlow()

    fun start() {
        scope.launch {
            authRepository.isLoggedIn.collect { isLoggedIn ->
                if (isLoggedIn) registerCurrentInstallation() else _currentInstallationId.value = null
            }
        }
    }

    fun onRegistered(installationId: String) {
        _currentInstallationId.value = PushInstallationPolicy.currentToken(
            authRepository.isLoggedIn.value, installationId
        )
    }

    private suspend fun registerCurrentInstallation() {
        if (!authRepository.isLoggedIn.value) return
        runCatching { messaging.register().await() }
            .onFailure { _currentInstallationId.value = null }
    }
}
