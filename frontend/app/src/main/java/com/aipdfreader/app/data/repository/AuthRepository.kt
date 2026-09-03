package com.aipdfreader.app.data.repository

import com.aipdfreader.app.data.local.session.SessionTokenStore
import com.aipdfreader.app.data.remote.AuthApi
import com.aipdfreader.app.data.remote.dto.LoginRequest
import com.aipdfreader.app.data.remote.dto.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    object Success : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * Owns the client side of OUR backend's Authentication component. This is
 * the only credential-bearing repository in the app — everything AI-related
 * (provider choice, keys, prompts) lives on the server.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionTokenStore: SessionTokenStore
) {
    private val _isLoggedIn = MutableStateFlow(sessionTokenStore.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val currentUserEmail: String? get() = sessionTokenStore.userEmail

    suspend fun login(email: String, password: String): AuthResult = try {
        val response = authApi.login(LoginRequest(email = email, password = password))
        sessionTokenStore.saveSession(response.accessToken, response.refreshToken, response.email ?: email)
        _isLoggedIn.value = true
        AuthResult.Success
    } catch (e: HttpException) {
        AuthResult.Failure(httpErrorMessage(e))
    } catch (e: Exception) {
        AuthResult.Failure(e.message ?: "Couldn't reach the server. Check your connection and try again.")
    }

    suspend fun register(email: String, password: String): AuthResult = try {
        val response = authApi.register(RegisterRequest(email = email, password = password))
        sessionTokenStore.saveSession(response.accessToken, response.refreshToken, response.email ?: email)
        _isLoggedIn.value = true
        AuthResult.Success
    } catch (e: HttpException) {
        AuthResult.Failure(httpErrorMessage(e))
    } catch (e: Exception) {
        AuthResult.Failure(e.message ?: "Couldn't reach the server. Check your connection and try again.")
    }

    fun logout() {
        sessionTokenStore.clear()
        _isLoggedIn.value = false
    }

    private fun httpErrorMessage(e: HttpException): String = when (e.code()) {
        401 -> "Incorrect email or password."
        409 -> "An account with that email already exists."
        in 500..599 -> "The server had a problem. Please try again shortly."
        else -> "Something went wrong (code ${e.code()})."
    }
}
