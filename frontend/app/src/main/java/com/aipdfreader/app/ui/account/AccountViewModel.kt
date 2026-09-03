package com.aipdfreader.app.ui.account

import androidx.lifecycle.ViewModel
import com.aipdfreader.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AccountUiState(
    val email: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState(email = authRepository.currentUserEmail))
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    fun logout(onLoggedOut: () -> Unit) {
        authRepository.logout()
        onLoggedOut()
    }
}
