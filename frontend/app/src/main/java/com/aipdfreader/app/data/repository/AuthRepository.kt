package com.aipdfreader.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data object Success : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/** Firebase owns the authentication session and ID-token refresh lifecycle. */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    private val _isLoggedIn = MutableStateFlow(firebaseAuth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        _isLoggedIn.value = auth.currentUser != null
    }

    init { firebaseAuth.addAuthStateListener(authListener) }

    val currentUserEmail: String? get() = firebaseAuth.currentUser?.email
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    suspend fun login(email: String, password: String): AuthResult = authenticate {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(email: String, password: String): AuthResult = authenticate {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await()
    }

    private suspend fun authenticate(block: suspend () -> Any): AuthResult = try {
        withContext(Dispatchers.IO) { block() }
        _isLoggedIn.value = true
        AuthResult.Success
    } catch (e: Exception) {
        AuthResult.Failure(authErrorMessage(e))
    }

    fun logout() {
        firebaseAuth.signOut()
        _isLoggedIn.value = false
    }

    private fun authErrorMessage(error: Throwable): String = when {
        error is FirebaseAuthInvalidCredentialsException || error is FirebaseAuthInvalidUserException ->
            "That email or password wasn’t accepted. Check your details and try again."
        error is FirebaseAuthException && error.errorCode.contains("EMAIL_ALREADY_IN_USE") ->
            "An account with that email already exists. Sign in instead."
        error is FirebaseAuthException && error.errorCode.contains("WEAK_PASSWORD") ->
            "Choose a stronger password (at least 6 characters)."
        error.message?.contains("network", ignoreCase = true) == true ->
            "Couldn’t reach Firebase. Check your connection and try again."
        else -> "We couldn’t complete sign-in. Please try again."
    }
}
