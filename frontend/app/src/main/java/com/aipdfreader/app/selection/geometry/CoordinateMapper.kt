package com.aipdfreader.app.selection.geometry

import com.aipdfreader.app.selection.model.NormalizedPoint
import com.aipdfreader.app.selection.model.StrokePoint
import com.aipdfreader.app.selection.model.ViewportSnapshot
import javax.inject.Inject

/**
 * Converts raw, screen-space [StrokePoint]s into canonical, rotation/zoom/
 * DPI-independent [NormalizedPoint]s — the single coordinate system the
 * rest of the Selection Engine operates in (approved design, §7).
 *
 * Two transforms are composed here, in this order:
 *
 * 1. **Undo the pinch-zoom transform.** The reader applies a
 *    `graphicsLayer(scale, translationX, translationY)` on top of the base
 *    page layout, pivoting around the viewport's center. This step inverts
 *    that to recover the touch position in *unzoomed* viewport-local pixels.
 * 2. **Undo the fit-to-viewport letterboxing.** The rendered page bitmap is
 *    displayed with `ContentScale.Fit` semantics — scaled uniformly to fit
 *    within the viewport and centered, which leaves letterbox bars on one
 *    axis unless the bitmap and viewport share an aspect ratio. This step
 *    maps the unzoomed viewport position into the bitmap's own local
 *    pixel space, then normalizes by the bitmap's dimensions.
 *
 * Because the *rendering* side (drawing a finalized polygon back on
 * screen) nests its drawing surface inside the same `graphicsLayer` the
 * page image uses, only step 2's inverse (a simple linear map) is needed to
 * go the other direction — see `ReaderScreen`'s lasso overlay.
 */
class CoordinateMapper @Inject constructor() {

    /**
     * Maps one point. Both transform steps are inlined into a single
     * function body (rather than composing two private functions via an
     * intermediate `Pair<Float, Float>`) purely to avoid boxing that pair
     * on every call — this runs once per (already-simplified) stroke point,
     * so for a typical stroke that's dozens of avoidable small allocations
     * per gesture. The two steps themselves — undo zoom, then undo
     * letterboxing — are unchanged from the class KDoc above.
     */
    fun map(point: StrokePoint, viewport: ViewportSnapshot): NormalizedPoint {
        val centerX = viewport.viewportWidthPx / 2f
        val centerY = viewport.viewportHeightPx / 2f
        val scale = viewport.scale.coerceAtLeast(MIN_SCALE)

        val unzoomedX = (point.x - viewport.offsetX - centerX) / scale + centerX
        val unzoomedY = (point.y - viewport.offsetY - centerY) / scale + centerY

        return undoLetterboxing(unzoomedX, unzoomedY, viewport)
    }

    fun mapAll(points: List<StrokePoint>, viewport: ViewportSnapshot): List<NormalizedPoint> =
        points.map { map(it, viewport) }

    /** Step 2: invert the `ContentScale.Fit` letterboxing to land in bitmap-normalized space. */
    private fun undoLetterboxing(
        unzoomedX: Float,
        unzoomedY: Float,
        viewport: ViewportSnapshot
    ): NormalizedPoint {
        val fitted = FittedRect.of(viewport)

        val u = (unzoomedX - fitted.left) / fitted.width
        val v = (unzoomedY - fitted.top) / fitted.height
        return NormalizedPoint(u, v)
    }

    companion object {
        private const val MIN_SCALE = 0.01f
    }
}

/**
 * The rectangle, in viewport-local pixels, that the page bitmap actually
 * occupies once scaled uniformly to fit the viewport and centered — i.e.
 * the same geometry `ContentScale.Fit` produces for the page `Image`.
 * Shared by [CoordinateMapper] (inverse direction) and the reader's lasso
 * overlay (forward direction) so both agree on exactly the same letterbox
 * math.
 */
data class FittedRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    companion object {
        fun of(viewport: ViewportSnapshot): FittedRect {
            val fitScale = minOf(
                viewport.viewportWidthPx / viewport.bitmapWidthPx,
                viewport.viewportHeightPx / viewport.bitmapHeightPx
            )
            val width = viewport.bitmapWidthPx * fitScale
            val height = viewport.bitmapHeightPx * fitScale
            val left = (viewport.viewportWidthPx - width) / 2f
            val top = (viewport.viewportHeightPx - height) / 2f
            return FittedRect(left, top, width, height)
        }
    }
}
