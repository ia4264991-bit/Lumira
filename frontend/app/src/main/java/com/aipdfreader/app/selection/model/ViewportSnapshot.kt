package com.aipdfreader.app.selection.model

/**
 * A snapshot of the reader's viewport state at the moment a lasso gesture
 * began. [StrokePath] carries one of these so a stroke can be mapped into
 * normalized page space deterministically later, even if the live viewport
 * changes in the meantime (see the approved Selection Engine design, §5.b
 * and §9 — "Zooming during selection").
 *
 * All values describe the *content* image (the rendered PDF page bitmap)
 * as displayed inside the reader's viewport:
 *
 * - [scale] / [offsetX] / [offsetY] — the current pinch-zoom transform
 *   applied on top of the base fit-to-viewport layout (matches the
 *   `graphicsLayer(scaleX, scaleY, translationX, translationY)` values
 *   already tracked by the reader's zoomable page view).
 * - [viewportWidthPx] / [viewportHeightPx] — the size, in pixels, of the
 *   untransformed container the gesture was detected on.
 * - [bitmapWidthPx] / [bitmapHeightPx] — the size, in pixels, of the
 *   rendered page bitmap itself, which may not fill the viewport exactly
 *   (it is letterboxed to fit while preserving aspect ratio).
 */
data class ViewportSnapshot(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val bitmapWidthPx: Float,
    val bitmapHeightPx: Float
)
