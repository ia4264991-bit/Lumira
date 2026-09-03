package com.aipdfreader.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.repository.PdfRepository
import com.aipdfreader.app.domain.model.PdfDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val documents: List<PdfDocument> = emptyList(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val pdfRepository: PdfRepository
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        pdfRepository.observeLibrary(),
        isImporting,
        errorMessage
    ) { documents, importing, error ->
        LibraryUiState(documents = documents, isImporting = importing, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    /** Returns the new document's id on success via [onImported], for immediate navigation. */
    fun importPdf(uri: Uri, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            isImporting.value = true
            errorMessage.value = null
            val id = pdfRepository.importPdf(uri)
            isImporting.value = false
            if (id != null) {
                onImported(id)
            } else {
                errorMessage.value = "Couldn't open that PDF. It may be corrupted or password-protected."
            }
        }
    }

    fun deleteDocument(document: PdfDocument) {
        viewModelScope.launch { pdfRepository.deleteDocument(document) }
    }

    fun dismissError() {
        errorMessage.value = null
    }
}
