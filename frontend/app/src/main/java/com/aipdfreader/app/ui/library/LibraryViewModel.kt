package com.aipdfreader.app.ui.library

import android.net.Uri
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.repository.PdfRepository
import com.aipdfreader.app.data.repository.LocalMaterialRepository
import com.aipdfreader.app.data.repository.LocalCardRepository
import com.aipdfreader.app.data.remote.CardApi
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.CardDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import com.aipdfreader.app.domain.model.PdfDocument
import com.aipdfreader.app.domain.model.LocalMaterial
import com.aipdfreader.app.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import java.io.File
import javax.inject.Inject

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)

sealed interface LibraryItem {
    val key: String
    val importedAtMillis: Long

    data class Pdf(val document: PdfDocument) : LibraryItem {
        override val key = "pdf:${document.id}"
        override val importedAtMillis = document.lastOpenedAtMillis
    }

    data class File(val material: LocalMaterial) : LibraryItem {
        override val key = "file:${material.id}"
        override val importedAtMillis = material.importedAtMillis
    }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val localMaterialRepository: LocalMaterialRepository,
    private val localCardRepository: LocalCardRepository,
    private val cardApi: CardApi,
    private val domainApi: DomainApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val _cardTargets = MutableStateFlow<List<CardDto>>(emptyList())
    val cardTargets: StateFlow<List<CardDto>> = _cardTargets
    private val _isAddingToCard = MutableStateFlow(false)
    val isAddingToCard: StateFlow<Boolean> = _isAddingToCard
    private val _isLoadingCardTargets = MutableStateFlow(false)
    val isLoadingCardTargets: StateFlow<Boolean> = _isLoadingCardTargets

    val uiState: StateFlow<LibraryUiState> = combine(
        pdfRepository.observeLibrary(),
        localMaterialRepository.observeMaterials(),
        isImporting,
        errorMessage
    ) { documents, materials, importing, error ->
        val items = documents.map { LibraryItem.Pdf(it) } + materials.map { LibraryItem.File(it) }
        LibraryUiState(items = items.sortedByDescending { it.importedAtMillis },
            isImporting = importing, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            errorMessage.value = null
            try {
                val (displayName, mimeType) = withContext(Dispatchers.IO) {
                    val name = FileUtils.queryDisplayName(context, uri).orEmpty()
                    name to FileUtils.resolveMimeType(context, uri, name)
                }
                val isPdf = mimeType.equals("application/pdf", ignoreCase = true) ||
                    displayName.endsWith(".pdf", ignoreCase = true)
                val success = if (isPdf) {
                    pdfRepository.importPdf(uri) != null
                } else {
                    localMaterialRepository.import(uri, mimeType)
                }
                if (!success) {
                    errorMessage.value = if (isPdf) {
                        "Couldn't save that PDF. It may be corrupted or password-protected."
                    } else {
                        "Couldn't save that file. Check that it is still available and try again."
                    }
                }
            } catch (_: Exception) {
                errorMessage.value = "Couldn't save that file. Check that it is still available and try again."
            } finally {
                isImporting.value = false
            }
        }
    }

    fun delete(item: LibraryItem) {
        viewModelScope.launch {
            when (item) {
                is LibraryItem.Pdf -> pdfRepository.deleteDocument(item.document)
                is LibraryItem.File -> localMaterialRepository.delete(item.material)
            }
        }
    }

    fun loadCardTargets() {
        viewModelScope.launch {
            _isLoadingCardTargets.value = true
            try {
                val local = localCardRepository.observeCards().first().map { card ->
                    CardDto("local:${card.id}", card.ownerUid, card.name, card.color, false, "OWNER")
                }
                val remote = runCatching {
                    supervisorScope {
                        val personal = async { cardApi.listCards() }
                        val shared = async { cardApi.listCards("shared") }
                        (personal.await() + shared.await())
                            .distinctBy { it.id }
                            .filter { !it.isShared || it.role.equals("OWNER", true) || it.role.equals("ADMIN", true) }
                    }
                }.getOrDefault(emptyList())
                _cardTargets.value = local + remote
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = failure.message ?: "Couldn’t load your Cards. Try again."
            } finally {
                _isLoadingCardTargets.value = false
            }
        }
    }

    fun addToCard(item: LibraryItem, cardId: String) {
        viewModelScope.launch {
            _isAddingToCard.value = true
            errorMessage.value = null
            runCatching {
                val (path, displayName) = when (item) {
                    is LibraryItem.Pdf -> item.document.filePath to "${item.document.title}.pdf"
                    is LibraryItem.File -> item.material.filePath to item.material.displayName
                }
                val file = File(path)
                if (!file.isFile) error("This saved file is no longer available.")
                if (cardId.startsWith("local:")) {
                    val mimeType = FileUtils.mimeTypeForFileName(displayName)
                        ?: when (item) {
                            is LibraryItem.File -> item.material.mimeType
                            is LibraryItem.Pdf -> "application/pdf"
                        }
                    if (!localCardRepository.addExistingFile(cardId.removePrefix("local:"), path,
                            displayName, mimeType, file.length())) error("Couldn’t copy this file into the Card.")
                    "Saved to this Card on your phone. It’s ready to open offline."
                } else {
                    val mimeType = FileUtils.mimeTypeForFileName(displayName)
                        ?: error("This file type isn’t supported in online Cards yet.")
                    val part = MultipartBody.Part.createFormData(
                        "file", displayName, file.asRequestBody(mimeType.toMediaType())
                    )
                    domainApi.uploadResource(cardId, part, displayName.substringBeforeLast('.', displayName))
                    "Added file to the Card."
                }
            }.onSuccess { message ->
                errorMessage.value = message
            }.onFailure {
                errorMessage.value = it.message ?: "Couldn’t add this file to the Card. Try again."
            }
            _isAddingToCard.value = false
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }
}
