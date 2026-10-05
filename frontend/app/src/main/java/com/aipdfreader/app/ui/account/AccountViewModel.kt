package com.aipdfreader.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.repository.AuthRepository
import com.aipdfreader.app.data.repository.LearnerProfile
import com.aipdfreader.app.data.repository.LearnerProfileStore
import com.aipdfreader.app.ui.theme.ThemeMode
import com.aipdfreader.app.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val email: String? = null,
    val profile: LearnerProfile? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val themePreferences: ThemePreferences,
    private val profileStore: LearnerProfileStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState(email = authRepository.currentUserEmail, profile = profileStore.current()))
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = themePreferences.mode

    fun setThemeMode(mode: ThemeMode) = themePreferences.setMode(mode)

    fun updateName(name: String) {
        val profile = profileStore.current() ?: return
        if (name.isBlank()) return
        profile.copy(name = name.trim()).let(profileStore::save)
        _uiState.value = _uiState.value.copy(profile = profile.copy(name = name.trim()))
    }

    fun setPhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = profileStore.setPhoto(uri) ?: return@launch
            _uiState.value = _uiState.value.copy(profile = profileStore.current()?.copy(photoPath = path))
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        authRepository.logout()
        onLoggedOut()
    }
}
