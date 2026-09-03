package com.aipdfreader.app.selection.model

/**
 * The finalized, validated lasso shape: a closed polygon in normalized
 * page-display space, ready for (in a later milestone) intersection against
 * page content. Vertices are stored without repeating the first point at
 * the end — renderers and geometry algorithms are expected to close the
 * loop themselves (i.e. treat `points.last()` as connected back to
 * `points.first()`).
 *
 * Only [com.aipdfreader.app.selection.geometry.PolygonBuilder] should
 * construct instances of this type, since it is responsible for validating
 * vertex count and rejecting degenerate (near-zero-area) shapes before a
 * [SelectionPolygon] ever exists.
 */
data class SelectionPolygon(
    val pageIndex: Int,
    val points: List<NormalizedPoint>
) {
    init {
        require(points.size >= MIN_VERTICES) {
            "SelectionPolygon requires at least $MIN_VERTICES vertices, got ${points.size}"
        }
    }

    /** Axis-aligned bounding box of this polygon, in normalized space. */
    fun boundingBox(): NormalizedRect = points.boundingBox()

    companion object {
        const val MIN_VERTICES = 3
    }
}

/** Axis-aligned rectangle in normalized page-display space. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
