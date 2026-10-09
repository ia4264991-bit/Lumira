package com.aipdfreader.app.ui.reader

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aipdfreader.app.domain.model.Highlight
import com.aipdfreader.app.selection.capture.LassoGestureController
import com.aipdfreader.app.selection.geometry.FittedRect
import com.aipdfreader.app.selection.model.SelectionPolygon
import com.aipdfreader.app.selection.model.StrokePath
import com.aipdfreader.app.selection.model.ViewportSnapshot
import com.aipdfreader.app.selection.model.boundingBox
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    pdfId: Long,
    onBack: () -> Unit,
    canAskSarahAboutResource: Boolean = false,
    onAskAi: (highlightId: Long?, selectedText: String, pageIndex: Int) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageListState = rememberLazyListState()

    LaunchedEffect(state.document?.id) {
        val document = state.document ?: return@LaunchedEffect
        pageListState.scrollToItem(state.currentPage.coerceIn(0, document.pageCount - 1))
        snapshotFlow {
            val layout = pageListState.layoutInfo
            mostVisiblePdfPageIndex(
                pages = layout.visibleItemsInfo.map { PdfViewportPage(it.index, it.offset, it.size) },
                viewportStart = layout.viewportStartOffset,
                viewportEnd = layout.viewportEndOffset
            )
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(viewModel::goToPage)
    }

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
                    onPrevious = {
                        scope.launch { pageListState.animateScrollToItem((state.currentPage - 1).coerceAtLeast(0)) }
                    },
                    onNext = {
                        scope.launch {
                            pageListState.animateScrollToItem((state.currentPage + 1).coerceAtMost(doc.pageCount - 1))
                        }
                    }
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
                val document = state.document
                if (document == null) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.isLoadingPage) CircularProgressIndicator()
                        Text(state.errorMessage ?: "Opening document…", modifier = Modifier.padding(top = 12.dp))
                    }
                } else {
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(count = document.pageCount, key = { pageIndex -> pageIndex }) { pageIndex ->
                            val bitmap = state.pageBitmaps[pageIndex]
                            val isRendering = pageIndex in state.renderingPageIndexes
                            val hasRenderError = pageIndex in state.pageErrors
                            LaunchedEffect(pdfId, pageIndex, bitmap == null, isRendering, hasRenderError) {
                                if (bitmap == null && !isRendering && !hasRenderError) {
                                    viewModel.ensurePageRendered(pageIndex)
                                }
                            }
                            val aspectRatio = state.pageAspectRatios[pageIndex]
                                ?: bitmap?.let { it.width.toFloat() / it.height }
                                ?: (1f / 1.414f)
                            Box(
                                Modifier.fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (bitmap != null && pageIndex == state.currentPage) {
                                    val onLassoStrokeCaptured = remember(viewModel) { viewModel::onLassoStrokeCaptured }
                                    val onLassoCornerMoved = remember(viewModel) { viewModel::moveLassoCorner }
                                    ZoomablePdfPage(
                                        bitmap = bitmap,
                                        highlightCount = state.pageHighlights.size,
                                        pageIndex = pageIndex,
                                        selectionTool = state.selectionTool,
                                        isRefiningLasso = state.isRefiningLasso,
                                        lassoPolygon = state.lassoPolygon,
                                        onLassoStrokeCaptured = onLassoStrokeCaptured,
                                        onLassoCornerMoved = onLassoCornerMoved
                                    )
                                } else if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "PDF page ${pageIndex + 1}",
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    )
                                } else {
                                    Column(
                                        Modifier.align(Alignment.Center).padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (pageIndex in state.renderingPageIndexes) CircularProgressIndicator()
                                        else {
                                            Text(state.pageErrors[pageIndex] ?: "This page could not be displayed.")
                                            TextButton(onClick = { viewModel.ensurePageRendered(pageIndex) }) { Text("Retry") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state.errorMessage?.let { message ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            if (state.selectionTool == SelectionTool.LASSO && state.isRefiningLasso) {
                LassoRefinementActionBar(onDone = viewModel::finishLassoRefinement)
            } else if (state.selectionTool == SelectionTool.LASSO && state.isResolvingLasso) {
                LassoResolvingActionBar()
            } else if (state.selectionTool == SelectionTool.LASSO && state.lassoPolygon != null) {
                LassoSelectionActionBar(
                    selectedText = state.currentSelection,
                    canAskSarahAboutResource = canAskSarahAboutResource,
                    onRefine = viewModel::beginLassoRefinement,
                    onShare = {
                        val bitmap = state.pageBitmap
                        val polygon = state.lassoPolygon
                        if (bitmap != null && polygon != null) {
                            scope.launch {
                                runCatching {
                                    val file = withContext(Dispatchers.IO) {
                                        createLassoShareImage(bitmap, polygon, context.cacheDir)
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        if (state.currentSelection.isNotBlank()) {
                                            putExtra(Intent.EXTRA_TEXT, state.currentSelection)
                                        }
                                        clipData = ClipData.newUri(context.contentResolver, "PDF selection", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share selection"))
                                }.onFailure {
                                    Toast.makeText(context, "Couldn’t prepare that selection to share.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onHighlight = { if (state.currentSelection.isNotBlank()) viewModel.saveHighlight(onSaved = {}) },
                    onAskAi = { text ->
                        if (text.isBlank()) {
                            onAskAi(null, "", state.currentPage)
                        } else {
                            viewModel.saveHighlight(onSaved = { highlightId ->
                                onAskAi(highlightId, text, state.currentPage)
                            })
                        }
                    },
                    onAskPage = { onAskAi(null, "", state.currentPage) },
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
                            onAskAi(highlightId, text, state.currentPage)
                        })
                    },
                    onSave = { viewModel.saveHighlight(onSaved = {}) }
                )
            }

            if (state.pageHighlights.isNotEmpty()) {
                HighlightRow(
                    highlights = state.pageHighlights,
                    onAskAboutHighlight = { onAskAi(it.id, it.selectedText, it.pageIndex) },
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePdfPage(
    bitmap: android.graphics.Bitmap?,
    highlightCount: Int,
    pageIndex: Int,
    selectionTool: SelectionTool,
    isRefiningLasso: Boolean,
    lassoPolygon: SelectionPolygon?,
    onLassoStrokeCaptured: (StrokePath) -> Unit,
    onLassoCornerMoved: (Int, Float, Float) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }

    val lassoGestureController = remember { LassoGestureController() }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        val maxOffsetX = (scale - 1f) * viewportWidthPx / 2f
        val maxOffsetY = (scale - 1f) * viewportHeightPx / 2f
        offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
    }

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

    LaunchedEffect(isRefiningLasso) {
        if (isRefiningLasso) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }
    LaunchedEffect(pageIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8))
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .onSizeChanged {
                viewportWidthPx = it.width.toFloat()
                viewportHeightPx = it.height.toFloat()
            }
            .then(
                if (selectionTool == SelectionTool.TEXT) {
                    Modifier.transformable(state = transformState, canPan = { scale > 1f })
                } else Modifier
            )
            .pointerInput(bitmap, selectionTool, isRefiningLasso) {
                when (selectionTool) {
                    SelectionTool.TEXT -> Unit

                    SelectionTool.LASSO -> {
                        if (isRefiningLasso) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                            return@pointerInput
                        }
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
                if (isRefiningLasso && lassoPolygon != null) {
                    LassoRefinementHandlesOverlay(
                        polygon = lassoPolygon,
                        bitmapWidthPx = bmp.width.toFloat(),
                        bitmapHeightPx = bmp.height.toFloat(),
                        onCornerMoved = onLassoCornerMoved
                    )
                }
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

/** Four touch handles let the reader adjust the rough lasso after drawing it. */
@Composable
private fun LassoRefinementHandlesOverlay(
    polygon: SelectionPolygon,
    bitmapWidthPx: Float,
    bitmapHeightPx: Float,
    onCornerMoved: (Int, Float, Float) -> Unit
) {
    val latestPolygon by rememberUpdatedState(polygon)
    val latestOnCornerMoved by rememberUpdatedState(onCornerMoved)
    val density = LocalDensity.current
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var activeHandle = -1
                val hitRadius = with(density) { 48.dp.toPx() }
                detectDragGestures(
                    onDragStart = { down ->
                        val current = latestPolygon
                        if (current == null) {
                            activeHandle = -1
                        } else {
                            val fit = fittedPageRect(size.width.toFloat(), size.height.toFloat(), bitmapWidthPx, bitmapHeightPx)
                            val handles = selectionHandlePositions(current, fit)
                            activeHandle = handles.indices.minByOrNull { index ->
                                val dx = handles[index].x - down.x
                                val dy = handles[index].y - down.y
                                dx * dx + dy * dy
                            } ?: -1
                            if (activeHandle >= 0) {
                                val dx = handles[activeHandle].x - down.x
                                val dy = handles[activeHandle].y - down.y
                                if (dx * dx + dy * dy > hitRadius * hitRadius) activeHandle = -1
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        if (activeHandle >= 0) {
                            change.consume()
                            val fit = fittedPageRect(size.width.toFloat(), size.height.toFloat(), bitmapWidthPx, bitmapHeightPx)
                            if (fit.width > 0f && fit.height > 0f) {
                                latestOnCornerMoved(
                                    activeHandle,
                                    ((change.position.x - fit.left) / fit.width).coerceIn(0f, 1f),
                                    ((change.position.y - fit.top) / fit.height).coerceIn(0f, 1f)
                                )
                            }
                        }
                    },
                    onDragEnd = { activeHandle = -1 },
                    onDragCancel = { activeHandle = -1 }
                )
            }
    ) {
        val fit = fittedPageRect(size.width, size.height, bitmapWidthPx, bitmapHeightPx)
        selectionHandlePositions(polygon, fit).forEach { handle ->
            drawCircle(Color.White, radius = 13.dp.toPx(), center = handle)
            drawCircle(LassoColor, radius = 13.dp.toPx(), center = handle, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

private fun fittedPageRect(viewportWidth: Float, viewportHeight: Float, bitmapWidth: Float, bitmapHeight: Float): FittedRect =
    FittedRect.of(
        ViewportSnapshot(
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            viewportWidthPx = viewportWidth,
            viewportHeightPx = viewportHeight,
            bitmapWidthPx = bitmapWidth,
            bitmapHeightPx = bitmapHeight
        )
    )

private fun selectionHandlePositions(polygon: SelectionPolygon, fit: FittedRect): List<Offset> {
    val bounds = polygon.boundingBox()
    return listOf(
        Offset(fit.left + bounds.left * fit.width, fit.top + bounds.top * fit.height),
        Offset(fit.left + bounds.right * fit.width, fit.top + bounds.top * fit.height),
        Offset(fit.left + bounds.right * fit.width, fit.top + bounds.bottom * fit.height),
        Offset(fit.left + bounds.left * fit.width, fit.top + bounds.bottom * fit.height)
    )
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

@Composable
private fun LassoRefinementActionBar(onDone: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Drag a corner to refine the area", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun LassoResolvingActionBar() {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Updating selection…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Actions available after the refined region has been confirmed. */
@Composable
private fun LassoSelectionActionBar(
    selectedText: String,
    canAskSarahAboutResource: Boolean,
    onRefine: () -> Unit,
    onShare: () -> Unit,
    onHighlight: () -> Unit,
    onAskAi: (String) -> Unit,
    onAskPage: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = if (selectedText.isBlank()) "Area selected · no searchable text found"
                else "\u201C${selectedText.take(70)}${if (selectedText.length > 70) "…" else ""}\u201D",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRefine) { Text("Adjust") }
                IconButton(onClick = onShare, modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Share selected area")
                }
                if (selectedText.isNotBlank()) {
                    TextButton(onClick = onHighlight) { Text("Highlight") }
                    Button(onClick = { onAskAi(selectedText) }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ask Sarah")
                    }
                } else if (canAskSarahAboutResource) {
                    TextButton(onClick = onAskPage) { Text("Ask Sarah about page") }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss selection", modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

private fun createLassoShareImage(source: Bitmap, polygon: SelectionPolygon, cacheDirectory: File): File {
    val bounds = polygon.boundingBox()
    val left = floor(bounds.left.coerceIn(0f, 1f) * source.width).toInt().coerceIn(0, source.width - 1)
    val top = floor(bounds.top.coerceIn(0f, 1f) * source.height).toInt().coerceIn(0, source.height - 1)
    val right = ceil(bounds.right.coerceIn(0f, 1f) * source.width).toInt().coerceIn(left + 1, source.width)
    val bottom = ceil(bounds.bottom.coerceIn(0f, 1f) * source.height).toInt().coerceIn(top + 1, source.height)
    val cropped = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
    try {
        val mask = android.graphics.Path().apply {
            polygon.points.forEachIndexed { index, point ->
                val x = point.u.coerceIn(0f, 1f) * source.width - left
                val y = point.v.coerceIn(0f, 1f) * source.height - top
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val canvas = android.graphics.Canvas(cropped)
        canvas.clipPath(mask)
        canvas.drawBitmap(source, -left.toFloat(), -top.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val directory = File(cacheDirectory, "lasso-shares").apply { mkdirs() }
        val file = File(directory, "selection-${UUID.randomUUID()}.png")
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    } finally {
        cropped.recycle()
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
