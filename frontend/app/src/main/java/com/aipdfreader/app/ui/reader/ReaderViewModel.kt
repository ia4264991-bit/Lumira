package com.aipdfreader.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.repository.HighlightRepository
import com.aipdfreader.app.data.repository.PdfRepository
import com.aipdfreader.app.domain.model.Highlight
import com.aipdfreader.app.domain.model.PdfDocument
import com.aipdfreader.app.pdf.PdfPageRenderer
import com.aipdfreader.app.selection.engine.SelectionEngine
import com.aipdfreader.app.selection.geometry.CoordinateMapper
import com.aipdfreader.app.selection.geometry.PolygonBuilder
import com.aipdfreader.app.selection.geometry.StrokeSimplifier
import com.aipdfreader.app.selection.model.SelectionPolygon
import com.aipdfreader.app.selection.model.SelectionResult
import com.aipdfreader.app.selection.model.StrokePath
import com.aipdfreader.app.selection.textlayout.TextLayoutCache
import com.aipdfreader.app.ui.theme.AmberHighlight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val document: PdfDocument? = null,
    val currentPage: Int = 0,
    val pageBitmap: Bitmap? = null,
    val pageText: String = "",
    val pageHighlights: List<Highlight> = emptyList(),
    val isLoadingPage: Boolean = true,
    val isTextPanelVisible: Boolean = false,
    val currentSelection: String = "",
    val errorMessage: String? = null,
    /** Which input method is active for selecting content on the page. */
    val selectionTool: SelectionTool = SelectionTool.TEXT,
    /**
     * The finalized lasso polygon for the current page, if any — rendered
     * by the existing overlay system ([PolygonOverlay]) regardless of
     * whether it resolved to any text (the user still sees what they drew).
     */
    val lassoPolygon: SelectionPolygon? = null,
    /**
     * The [SelectionEngine]'s resolution of [lassoPolygon] against the
     * current page's content, once available. `null` while a stroke is
     * still resolving, if the polygon was degenerate, or if resolution
     * failed — [currentSelection] is the field everything downstream
     * (highlighting, Ask AI) actually consumes; this is kept alongside it
     * per M1.3 requirement 3, for any future consumer that wants the full
     * result (matched blocks, bounding rect) rather than just the text.
     */
    val lassoSelectionResult: SelectionResult? = null
)

private const val PDF_ID_ARG = "pdfId"

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pdfRepository: PdfRepository,
    private val highlightRepository: HighlightRepository,
    private val strokeSimplifier: StrokeSimplifier,
    private val coordinateMapper: CoordinateMapper,
    private val polygonBuilder: PolygonBuilder,
    private val textLayoutCache: TextLayoutCache,
    private val selectionEngine: SelectionEngine
) : ViewModel() {

    private val pdfId: Long = checkNotNull(savedStateHandle[PDF_ID_ARG])

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var renderer: PdfPageRenderer? = null
    private var highlightsJob: Job? = null
    private var selectionResolutionJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch {
            pdfRepository.markOpened(pdfId)
            val document = pdfRepository.getDocument(pdfId)
            if (document == null) {
                _uiState.update { it.copy(errorMessage = "This document could not be found.") }
                return@launch
            }
            _uiState.update { it.copy(document = document, currentPage = document.lastReadPage) }
            renderer = PdfPageRenderer(document.filePath)
            loadPage(document.lastReadPage)
        }
    }

    fun goToPage(index: Int) {
        val document = _uiState.value.document ?: return
        val clamped = index.coerceIn(0, document.pageCount - 1)
        if (clamped == _uiState.value.currentPage) return
        _uiState.update { it.copy(currentPage = clamped) }
        loadPage(clamped)
        viewModelScope.launch { pdfRepository.saveLastReadPage(pdfId, clamped) }
    }

    fun nextPage() = goToPage(_uiState.value.currentPage + 1)
    fun previousPage() = goToPage(_uiState.value.currentPage - 1)

    fun toggleTextPanel() {
        _uiState.update { it.copy(isTextPanelVisible = !it.isTextPanelVisible) }
    }

    /**
     * Switches the active [SelectionTool]. Switching tools cancels any
     * in-flight lasso resolution and clears every piece of selection state
     * — polygon, resolved result, and [ReaderUiState.currentSelection] —
     * so a selection made under one tool never bleeds into the other (a
     * gap identified during the M1.3 integration review: previously only
     * [ReaderUiState.lassoPolygon] was cleared here, leaving a stale
     * `currentSelection` from a resolved lasso visible — and actionable —
     * after switching to the text panel, or vice versa).
     */
    fun setSelectionTool(tool: SelectionTool) {
        if (_uiState.value.selectionTool == tool) return
        selectionResolutionJob?.cancel()
        _uiState.update {
            it.copy(
                selectionTool = tool,
                lassoPolygon = null,
                lassoSelectionResult = null,
                currentSelection = ""
            )
        }
    }

    /**
     * Entry point for a finalized lasso gesture. Runs the M1.0 geometry
     * pipeline (simplify → map → build) to get a polygon, then — new in
     * M1.3 — resolves that polygon into actual selected text by fetching
     * the current page's layout from the existing [TextLayoutCache] and
     * handing both to the existing [SelectionEngine], completely unchanged
     * from how M1.2 left them.
     *
     * [SelectionResult.selectedText] is routed into [onSelectionChanged] —
     * the exact same entry point the long-press text panel already uses —
     * so highlighting, Ask AI, and everything downstream of "the user has
     * selected some text" work identically regardless of which tool
     * produced that text. Nothing about matching, intersection, or overlap
     * thresholds is reimplemented here; this function only orchestrates
     * calls to existing, unmodified components.
     */
    fun onLassoStrokeCaptured(stroke: StrokePath) {
        selectionResolutionJob?.cancel()
        selectionResolutionJob = viewModelScope.launch(Dispatchers.Default) {
            val simplifiedPoints = strokeSimplifier.simplify(
                points = stroke.points,
                currentZoomScale = stroke.viewportSnapshot.scale
            )
            val mappedPoints = coordinateMapper.mapAll(simplifiedPoints, stroke.viewportSnapshot)
            val polygon = polygonBuilder.build(stroke.pageIndex, mappedPoints)

            if (polygon == null) {
                // Degenerate stroke (too small / too few points after
                // simplification) — nothing to show or resolve.
                clearLassoState()
                return@launch
            }

            // Show the drawn shape immediately via the existing overlay
            // system, before resolution finishes, so drawing feels
            // immediate regardless of how long matching takes.
            _uiState.update { it.copy(lassoPolygon = polygon) }

            try {
                val layout = textLayoutCache.getContentLayout(pdfId, stroke.pageIndex)
                val result = selectionEngine.resolve(polygon, layout)

                _uiState.update { it.copy(lassoSelectionResult = result) }
                onSelectionChanged(result.selectedText)
            } catch (e: CancellationException) {
                // M1.4 structured-concurrency fix: this coroutine is
                // cancelled (a new stroke started, the tool changed, or the
                // page changed) precisely when its result would be stale.
                // A bare `catch (e: Exception)` here would have swallowed
                // this and still run the cleanup below, potentially
                // clearing state a *newer*, still-running resolution had
                // already set — rethrowing lets cancellation actually stop
                // this coroutine instead of racing with the one that
                // superseded it.
                throw e
            } catch (e: Exception) {
                // Resolution failed (e.g. layout extraction error). Fail
                // gracefully per M1.3 requirement 9: clear the temporary
                // lasso state quietly rather than surfacing a page-level
                // error — the rest of the reader (current page, highlights,
                // chat) is completely unaffected.
                clearLassoState()
            }
        }
    }

    /** Resets every piece of lasso-selection state at once — used by both the failure path above and [clearLassoPolygon]. */
    private fun clearLassoState() {
        _uiState.update {
            it.copy(lassoPolygon = null, lassoSelectionResult = null, currentSelection = "")
        }
    }

    /** Discards the current lasso polygon and any resolved selection, cancelling in-flight resolution if any. */
    fun clearLassoPolygon() {
        selectionResolutionJob?.cancel()
        clearLassoState()
    }

    fun onSelectionChanged(text: String) {
        _uiState.update { it.copy(currentSelection = text) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(currentSelection = "") }
    }

    /** Saves the current selection as a highlight and returns its id via [onSaved]. */
    fun saveHighlight(colorArgb: Int = AmberHighlight.toArgb(), onSaved: (Long) -> Unit) {
        val selection = _uiState.value.currentSelection
        if (selection.isBlank()) return
        viewModelScope.launch {
            val id = highlightRepository.save(
                pdfId = pdfId,
                pageIndex = _uiState.value.currentPage,
                selectedText = selection,
                colorArgb = colorArgb
            )
            onSaved(id)
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch { highlightRepository.delete(highlight) }
    }

    private fun loadPage(pageIndex: Int) {
        val currentRenderer = renderer ?: return

        // Cancel any resolution still running for the page being left, and
        // clear every piece of selection state immediately — not just once
        // the new page's bitmap/text finish loading below. Previously
        // `currentSelection` lingered from the old page until that async
        // load completed, which could briefly show stale Highlight/Ask AI
        // affordances for content that no longer matches what's on screen;
        // identified during the M1.3 integration review.
        selectionResolutionJob?.cancel()
        prefetchJob?.cancel()
        _uiState.update {
            it.copy(
                isLoadingPage = true,
                lassoPolygon = null,
                lassoSelectionResult = null,
                currentSelection = ""
            )
        }

        highlightsJob?.cancel()
        highlightsJob = viewModelScope.launch {
            highlightRepository.observeForPage(pdfId, pageIndex).collect { highlights ->
                _uiState.update { it.copy(pageHighlights = highlights) }
            }
        }

        viewModelScope.launch {
            try {
                val bitmap = currentRenderer.renderPage(pageIndex, targetWidthPx = 1600)
                val text = pdfRepository.extractPageText(_uiState.value.document!!.filePath, pageIndex)
                _uiState.update {
                    it.copy(
                        pageBitmap = bitmap,
                        pageText = text,
                        isLoadingPage = false,
                        // Defensive, same reasoning as the immediate reset
                        // above: guards against a lasso stroke captured on
                        // the still-visible previous bitmap resolving after
                        // this point completes (the resolution job for it
                        // was already cancelled above, but this keeps every
                        // selection-related field moving together rather
                        // than resetting currentSelection alone).
                        lassoPolygon = null,
                        lassoSelectionResult = null,
                        currentSelection = ""
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingPage = false, errorMessage = "Couldn't render this page.")
                }
            }
        }

        // Opportunistically warm TextLayoutCache for this page so the
        // *first* lasso stroke a user draws resolves as fast as every
        // subsequent one, rather than only warming the cache reactively on
        // first use. Best-effort only: a failure here has no user-visible
        // effect (onLassoStrokeCaptured will simply populate the cache
        // itself, on demand, the same way it always could) and must never
        // surface an error for what is purely a performance optimization.
        //
        // Tracked and cancelled like the other page-scoped jobs (M1.4 fix):
        // previously this coroutine was launched fire-and-forget with no
        // Job reference, so flipping pages quickly could leave several
        // superseded prefetches for pages the user has already left running
        // concurrently instead of being cancelled immediately. (Already
        // cancelled above, alongside selectionResolutionJob, at the top of
        // this function — nothing runs between that point and here that
        // could set prefetchJob again, so no second cancel is needed.)
        prefetchJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                textLayoutCache.getContentLayout(pdfId, pageIndex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort only; see comment above.
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        renderer?.close()
    }
}
