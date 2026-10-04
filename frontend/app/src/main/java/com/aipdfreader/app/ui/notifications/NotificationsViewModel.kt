package com.aipdfreader.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.NotificationDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val api: DomainApi) : ViewModel() {
    private val _items = MutableStateFlow<List<NotificationDto>>(emptyList())
    val items = _items.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            runCatching { api.notifications() }.onSuccess { _items.value = it; _message.value = null }
                .onFailure { _message.value = "Couldn’t load notifications. Check your connection and try again." }
        }
    }
    fun markRead(id: String) {
        viewModelScope.launch {
            runCatching { api.markNotificationRead(id) }
                .onSuccess { item -> _items.value = _items.value.map { if (it.id == id) item else it } }
                .onFailure { _message.value = "That notification couldn’t be marked read." }
        }
    }
}
