package com.aipdfreader.app.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aipdfreader.app.domain.model.Highlight
import com.aipdfreader.app.selection.capture.LassoGestureController
import com.aipdfreader.app.selection.geometry.FittedRect
import com.aipdfreader.app.selection.model.SelectionPolygon
import com.aipdfreader.app.selection.model.StrokePath
import com.aipdfreader.app.selection.model.ViewportSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    pdfId: Long,
    onBack: () -> Unit,
    onAskAi: (highlightId: Long?, selectedText: String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.document?.title ?: "Reading",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // The existing text-selection toggle is only meaningful in TEXT
                    // mode — preserved exactly as before, just conditionally shown.
                    if (state.selectionTool == SelectionTool.TEXT) {
                        IconButton(onClick = viewModel::toggleTextPanel) {
                            Icon(
                                Icons.Filled.TextFields,
                                contentDescription = "Toggle selectable text",
                                tint = if (state.isTextPanelVisible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            state.document?.let { doc ->
                PageNavigationBar(
                    currentPage = state.currentPage,
                    pageCount = doc.pageCount,
                    onPrevious = viewModel::previousPage,
                    onNext = viewModel::nextPage
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SelectionToolToggle(
                selectedTool = state.selectionTool,
                onToolSelected = viewModel::setSelectionTool,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoadingPage && state.pageBitmap == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    // `viewModel::onLassoStrokeCaptured` is a bound method
                    // reference — evaluating that expression allocates a new
                    // function object every time this composable's body
                    // re-executes, even though `viewModel` itself is stable
                    // for the screen's lifetime. Since ZoomablePdfPage reads
                    // it on every pointer-move during an active drag, an
                    // unstable reference here can defeat recomposition
                    // skipping for the whole page/overlay tree on totally
                    // unrelated state changes (e.g. a highlight added
                    // elsewhere). Wrapping it in `remember(viewModel)` gives
                    // it stable identity across recompositions (M1.4).
                    val onLassoStrokeCaptured = remember(viewModel) { viewModel::onLassoStrokeCaptured }
                    ZoomablePdfPage(
                        bitmap = state.pageBitmap,
                        highlightCount = state.pageHighlights.size,
                        pageIndex = state.currentPage,
                        selectionTool = state.selectionTool,
                        lassoPolygon = state.lassoPolygon,
                        onLassoStrokeCaptured = onLassoStrokeCaptured
                    )
                }
            }

            if (state.selectionTool == SelectionTool.LASSO && state.currentSelection.isNotBlank()) {
                // Reuses the exact same saveHighlight/onAskAi wiring the
                // TEXT-mode panel below uses — no new highlight-saving or
                // chat-routing logic is introduced for the lasso path.
                LassoSelectionActionBar(
                    selectedText = state.currentSelection,
                    onHighlight = { viewModel.saveHighlight(onSaved = {}) },
                    onAskAi = { text ->
                        viewModel.saveHighlight(onSaved = { highlightId ->
                            onAskAi(highlightId, text)
                        })
                    },
                    onDismiss = viewModel::clearLassoPolygon
                )
            }

            if (state.selectionTool == SelectionTool.TEXT && state.isTextPanelVisible) {
                SelectableTextPanel(
                    text = state.pageText,
                    selection = state.currentSelection,
                    onSelectionChanged = viewModel::onSelectionChanged,
                    onAskAi = { text ->
                        viewModel.saveHighlight(onSaved = { highlightId ->
                            onAskAi(highlightId, text)
                        })
                    },
                    onSave = { viewModel.saveHighlight(onSaved = {}) }
                )
            }

            if (state.pageHighlights.isNotEmpty()) {
                HighlightRow(
                    highlights = state.pageHighlights,
                    onAskAboutHighlight = { onAskAi(it.id, it.selectedText) },
                    onDelete = viewModel::deleteHighlight
                )
            }
        }
    }
}

/**
 * Lets the user pick which input method they're using to select page
 * content. Built as an exhaustive `when` over [SelectionTool] on purpose —
 * adding a future tool (see [SelectionTool]'s KDoc) means adding one more
 * `SegmentedButton`, not restructuring this composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionToolToggle(
    selectedTool: SelectionTool,
    onToolSelected: (SelectionTool) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = selectedTool == SelectionTool.TEXT,
            onClick = { onToolSelected(SelectionTool.TEXT) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Filled.TextFields, contentDescription = null) }
        ) {
            Text("Text")
        }
        SegmentedButton(
            selected = selectedTool == SelectionTool.LASSO,
            onClick = { onToolSelected(SelectionTool.LASSO) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.Filled.Gesture, contentDescription = null) }
        ) {
            Text("Lasso")
        }
    }
}

/**
 * Renders the current PDF page with pinch-to-zoom/pan (unchanged from
 * before), plus — when [selectionTool] is [SelectionTool.LASSO] — freeform
 * stroke capture and live polygon rendering.
 *
 * Two coordinate spaces are deliberately kept separate here:
 * - The in-progress raw stroke is drawn in the *outer*, untransformed
 *   viewport space it was captured in — a direct 1:1 draw, no mapping
 *   needed, since it must visually track the finger regardless of zoom.
 * - The finalized [lassoPolygon] is in normalized page-display space and is
 *   drawn inside the *same* `graphicsLayer` transform the page image uses,
 *   so it automatically pans/zooms together with the page content without
 *   this composable re-deriving the pinch-zoom transform itself.
 *
 * Per the approved Milestone 1 scope, pinch-zoom is paused while
 * [SelectionTool.LASSO] is active (single-finger draw only; a second
 * pointer cancels the in-progress stroke) — see [LassoGestureController]'s
 * KDoc for the full rationale.
 */
@Composable
private fun ZoomablePdfPage(
    bitmap: android.graphics.Bitmap?,
    highlightCount: Int,
    pageIndex: Int,
    selectionTool: SelectionTool,
    lassoPolygon: SelectionPolygon?,
    onLassoStrokeCaptured: (StrokePath) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val lassoGestureController = remember { LassoGestureController() }

    // Imperative Path for the in-progress stroke — mutated directly, never
    // stored as a Compose List in state. `liveStrokeVersion` is the only
    // piece of Compose state involved in live drawing: a cheap Int bump
    // that tells the Canvas below "redraw now", keeping per-point cost to a
    // single field write plus one recomposition of a small draw call,
    // regardless of how many points a stroke accumulates.
    val liveStrokePath = remember { Path() }
    var liveStrokeVersion by remember { mutableIntStateOf(0) }

    val polygonAlpha by animateFloatAsState(
        targetValue = if (lassoPolygon != null) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "lassoPolygonAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8))
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .pointerInput(bitmap, selectionTool) {
                when (selectionTool) {
                    SelectionTool.TEXT -> {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val maxOffsetX = (scale - 1f) * size.width / 2f
                            val maxOffsetY = (scale - 1f) * size.height / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        }
                    }

                    SelectionTool.LASSO -> {
                        lassoGestureController.detectLassoGesture(
                            scope = this,
                            pageIndex = pageIndex,
                            captureViewportSnapshot = {
                                // `size` here resolves to this pointerInput
                                // block's PointerInputScope.size — the same
                                // container the lasso gesture is detected
                                // on — captured lexically at lambda-creation
                                // time, so it stays correct however deep the
                                // callback is eventually invoked from.
                                ViewportSnapshot(
                                    scale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    viewportWidthPx = size.width.toFloat(),
                                    viewportHeightPx = size.height.toFloat(),
                                    bitmapWidthPx = bitmap?.width?.toFloat() ?: size.width.toFloat(),
                                    bitmapHeightPx = bitmap?.height?.toFloat() ?: size.height.toFloat()
                                )
                            },
                            onStrokeStarted = {
                                liveStrokePath.reset()
                                liveStrokeVersion++
                            },
                            onPointCaptured = { point ->
                                if (liveStrokePath.isEmpty) {
                                    liveStrokePath.moveTo(point.x, point.y)
                                } else {
                                    liveStrokePath.lineTo(point.x, point.y)
                                }
                                liveStrokeVersion++
                            },
                            onStrokeFinished = { strokePath ->
                                onLassoStrokeCaptured(strokePath)
                                liveStrokePath.reset()
                                liveStrokeVersion++
                            },
                            onStrokeCancelled = {
                                liveStrokePath.reset()
                                liveStrokeVersion++
                            }
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )

                // Finalized polygon — normalized-space, rides the same zoom
                // transform as the page image above (see class KDoc).
                PolygonOverlay(
                    polygon = lassoPolygon,
                    alpha = polygonAlpha,
                    bitmapWidthPx = bmp.width.toFloat(),
                    bitmapHeightPx = bmp.height.toFloat()
                )
            }
        }

        // In-progress raw stroke — outer/untransformed space, tracks the
        // finger 1:1 regardless of current zoom (see class KDoc).
        if (selectionTool == SelectionTool.LASSO) {
            LiveStrokeOverlay(path = liveStrokePath, version = liveStrokeVersion)
        }

        if (highlightCount > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$highlightCount highlight${if (highlightCount > 1) "s" else ""}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Shared stroke/fill color for both the live stroke and finalized polygon. */
private val LassoColor = Color(0xFF2962FF)

/**
 * Draws the finalized [polygon], if any, mapping its normalized
 * page-display-space vertices into this composable's own local pixel space
 * via [FittedRect]. Deliberately its own composable (rather than inlined
 * into [ZoomablePdfPage]) so a polygon change or fade-in tick only
 * recomposes this small draw call — the page `Image` and sibling overlays
 * are untouched.
 */
@Composable
private fun PolygonOverlay(
    polygon: SelectionPolygon?,
    alpha: Float,
    bitmapWidthPx: Float,
    bitmapHeightPx: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val safePolygon = polygon ?: return@Canvas
        val viewport = ViewportSnapshot(
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            viewportWidthPx = size.width,
            viewportHeightPx = size.height,
            bitmapWidthPx = bitmapWidthPx,
            bitmapHeightPx = bitmapHeightPx
        )
        val fitted = FittedRect.of(viewport)

        val path = Path()
        safePolygon.points.forEachIndexed { index, point ->
            val x = fitted.left + point.u * fitted.width
            val y = fitted.top + point.v * fitted.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawPath(path, color = LassoColor.copy(alpha = 0.18f * alpha))
        drawPath(
            path,
            color = LassoColor.copy(alpha = alpha),
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Draws the in-progress raw stroke directly — a 1:1 draw in outer viewport
 * pixels, no coordinate mapping (see [ZoomablePdfPage]'s KDoc for why).
 *
 * [version] is the only Compose-state read involved: it scopes
 * recomposition to just this composable, while the actual point data lives
 * in the imperative [path] object mutated by [LassoGestureController]'s
 * callbacks. This keeps the per-point cost of a fast, long drag to one Int
 * increment plus one small draw call — never a growing Compose-tracked
 * list, and never a recomposition of the rest of the page tree.
 */
@Composable
private fun LiveStrokeOverlay(path: Path, version: Int) {
    // Reading `version` here is what makes this composable recompose (and
    // therefore redraw) each time a new point is accepted. The value itself
    // is unused — only the state read matters — so it's assigned to a
    // deliberately-unused local rather than left as a bare, warning-prone
    // expression statement.
    @Suppress("UNUSED_VARIABLE")
    val recomposeOnVersionChange = version

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (!path.isEmpty) {
            drawPath(
                path,
                color = LassoColor,
                style = Stroke(
                    width = 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.cornerPathEffect(8f)
                )
            )
        }
    }
}

/**
 * Shown once a lasso stroke has resolved to non-blank text. Reuses the
 * exact same actions (`saveHighlight`, the `onAskAi` navigation callback)
 * the long-press [SelectableTextPanel] already exposes — this composable
 * only presents them, it does not implement anything new for highlighting
 * or chat routing (M1.3 requirement 6/7).
 */
@Composable
private fun LassoSelectionActionBar(
    selectedText: String,
    onHighlight: () -> Unit,
    onAskAi: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u201C${selectedText.take(40)}${if (selectedText.length > 40) "…" else ""}\u201D",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss selection", modifier = Modifier.height(18.dp))
            }
            TextButton(onClick = onHighlight) {
                Text("Highlight")
            }
            Button(onClick = { onAskAi(selectedText) }) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ask Sarah")
            }
        }
    }
}

/**
 * The real, selectable representation of the page's text (extracted via
 * PdfBox). Users long-press to select a passage here — the bitmap page above
 * is the visual, this panel is where "select text" and "highlight" actually
 * operate, since Android's PdfRenderer output is a flat image with no text
 * layer of its own.
 *
 * Unchanged by the Selection Engine work — this remains the [SelectionTool.TEXT]
 * code path exactly as it was before lasso selection was introduced.
 */
@Composable
private fun SelectableTextPanel(
    text: String,
    selection: String,
    onSelectionChanged: (String) -> Unit,
    onAskAi: (String) -> Unit,
    onSave: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select text from this page", style = MaterialTheme.typography.labelMedium)
                if (selection.isNotBlank()) {
                    Text(
                        "${selection.length} chars selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (text.isBlank()) {
                    Text(
                        "No extractable text found on this page (it may be a scanned image).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // SelectionContainer gives real, native long-press text
                    // selection with drag handles, copy, etc.
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        val copied = clipboardManager.getText()?.text.orEmpty()
                        if (copied.isNotBlank()) onSelectionChanged(copied)
                    }
                ) {
                    Text("Use copied text")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSave, enabled = selection.isNotBlank()) {
                    Text("Highlight")
                }
                Button(
                    onClick = { onAskAi(selection) },
                    enabled = selection.isNotBlank()
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ask Sarah")
                }
            }
        }
    }
}

@Composable
private fun HighlightRow(
    highlights: List<Highlight>,
    onAskAboutHighlight: (Highlight) -> Unit,
    onDelete: (Highlight) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(highlights, key = { it.id }) { highlight ->
            AssistChip(
                onClick = { onAskAboutHighlight(highlight) },
                label = {
                    Text(
                        highlight.selectedText.take(28) + if (highlight.selectedText.length > 28) "…" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onDelete(highlight) }, modifier = Modifier.height(20.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove highlight", modifier = Modifier.height(16.dp))
                    }
                }
            )
        }
    }
}

@Composable
private fun PageNavigationBar(
    currentPage: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, enabled = currentPage > 0) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
            }
            Text(
                "Page ${currentPage + 1} of $pageCount",
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = onNext, enabled = currentPage < pageCount - 1) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
            }
        }
    }
}
