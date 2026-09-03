package com.aipdfreader.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.repository.AuthRepository
import com.aipdfreader.app.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { LOGIN, REGISTER }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.LOGIN,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** True if a session already exists — lets the caller skip straight past login. */
    val isAlreadyAuthenticated: Boolean get() = authRepository.isLoggedIn.value

    fun onEmailChanged(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onPasswordChanged(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            mode = if (_uiState.value.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
            errorMessage = null
        )
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Enter both an email and a password.")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            val result = if (state.mode == AuthMode.LOGIN) {
                authRepository.login(state.email.trim(), state.password)
            } else {
                authRepository.register(state.email.trim(), state.password)
            }

            when (result) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    onSuccess()
                }
                is AuthResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
                }
            }
        }
    }
}
