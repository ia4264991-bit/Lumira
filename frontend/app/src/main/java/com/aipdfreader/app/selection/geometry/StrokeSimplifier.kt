package com.aipdfreader.app.selection.geometry

import com.aipdfreader.app.selection.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import javax.inject.Inject

/**
 * Reduces a captured stroke's point count using the Ramer–Douglas–Peucker
 * algorithm, before the stroke is mapped into normalized page space.
 *
 * Per the approved design (§6, §8), the simplification tolerance is
 * expressed in *content-space* terms and scaled by the current zoom level
 * before being applied to the raw, screen-space points. This keeps
 * simplification behavior consistent regardless of how zoomed in the user
 * is: the same visual precision is preserved whether a stroke was drawn at
 * 1x or 4x zoom, rather than the tolerance silently becoming looser or
 * tighter on screen as zoom changes.
 *
 * Pure function; per call, exactly two allocations regardless of stroke
 * length or recursion depth (a `BooleanArray` and the final result list) —
 * see [markKeep]'s KDoc. Safe to call from a background dispatcher for
 * every finalized stroke.
 */
class StrokeSimplifier @Inject constructor() {

    /**
     * @param points the raw (already thinned) stroke points, in screen space.
     * @param baseToleranceContentPx the desired simplification tolerance,
     *   expressed as if the content were unzoomed (scale = 1).
     * @param currentZoomScale the pinch-zoom scale in effect when the stroke
     *   was drawn (from [com.aipdfreader.app.selection.model.ViewportSnapshot.scale]).
     */
    fun simplify(
        points: List<StrokePoint>,
        baseToleranceContentPx: Float = DEFAULT_BASE_TOLERANCE_PX,
        currentZoomScale: Float
    ): List<StrokePoint> {
        if (points.size <= 2) return points

        val effectiveScale = currentZoomScale.coerceAtLeast(MIN_SCALE)
        val pixelTolerance = baseToleranceContentPx * effectiveScale

        // Index-marking RDP: identical algorithm and output to a recursive
        // subList-and-merge implementation, but the recursion here only
        // walks index ranges over the original list and flips flags in one
        // shared BooleanArray — it never slices or concatenates a list at
        // each recursion level. The only two allocations for the whole call
        // are `keep` and the final `result`, regardless of stroke length or
        // recursion depth (previously: two new lists — from `dropLast` and
        // `+` — at every recursive split).
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        markKeep(points, 0, points.size - 1, pixelTolerance, keep)

        val result = ArrayList<StrokePoint>(points.size)
        for (i in points.indices) {
            if (keep[i]) result.add(points[i])
        }
        return result
    }

    /** Marks, in [keep], every point between [startIndex] and [endIndex] (inclusive) that RDP would retain. */
    private fun markKeep(points: List<StrokePoint>, startIndex: Int, endIndex: Int, epsilon: Float, keep: BooleanArray) {
        if (endIndex <= startIndex + 1) return // fewer than one interior point; nothing to consider

        val start = points[startIndex]
        val end = points[endIndex]

        var maxDistance = 0f
        var splitIndex = -1
        for (i in startIndex + 1 until endIndex) {
            val distance = perpendicularDistance(points[i], start, end)
            if (distance > maxDistance) {
                maxDistance = distance
                splitIndex = i
            }
        }

        if (maxDistance > epsilon && splitIndex != -1) {
            keep[splitIndex] = true
            markKeep(points, startIndex, splitIndex, epsilon, keep)
            markKeep(points, splitIndex, endIndex, epsilon, keep)
        }
    }

    private fun perpendicularDistance(point: StrokePoint, lineStart: StrokePoint, lineEnd: StrokePoint): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y

        if (dx == 0f && dy == 0f) {
            return hypot(point.x - lineStart.x, point.y - lineStart.y)
        }

        val normalization = sqrt(dx * dx + dy * dy)
        val numerator = abs(
            dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x
        )
        return numerator / normalization
    }

    companion object {
        const val DEFAULT_BASE_TOLERANCE_PX = 3f
        private const val MIN_SCALE = 0.1f
    }
}
