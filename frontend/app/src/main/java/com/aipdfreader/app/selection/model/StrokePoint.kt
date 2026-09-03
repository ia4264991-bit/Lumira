package com.aipdfreader.app.selection.model

/**
 * A single raw touch sample captured during a freeform (lasso) gesture, in
 * screen-space pixels relative to the untransformed viewport the gesture was
 * detected on (see [ViewportSnapshot] for how that viewport's pan/zoom state
 * is captured alongside a full stroke).
 *
 * Pure data — no Android or Compose dependency — so it can be unit tested
 * on the JVM without instrumentation.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val timestampMs: Long
)
