package com.aipdfreader.app.selection.model

/**
 * The complete captured freeform (lasso) gesture for one page: every
 * [StrokePoint] that survived [com.aipdfreader.app.selection.capture.StrokeThinner]
 * during capture, plus the [ViewportSnapshot] taken when the gesture began.
 *
 * This is the raw, screen-space input to the geometry pipeline
 * (simplify → map → build polygon). It is intentionally dumb data with no
 * behavior — all transformation logic lives in `selection.geometry`.
 */
data class StrokePath(
    val pageIndex: Int,
    val points: List<StrokePoint>,
    val viewportSnapshot: ViewportSnapshot
) {
    val pointCount: Int get() = points.size
    val isEmpty: Boolean get() = points.isEmpty()
}
