package com.aipdfreader.app.selection.model

/**
 * A point in canonical **normalized page-display space**: `(u, v) ∈
 * [0,1]×[0,1]`, top-left origin, already rotation-corrected to match what's
 * rendered on screen, independent of zoom level and device pixel density.
 *
 * This is the one coordinate system every layer of the Selection Engine —
 * capture, geometry, and (in later milestones) text layout — converges on.
 * See the approved Selection Engine design, §7, for the full rationale.
 *
 * Values are not strictly clamped to `[0,1]` at construction time, since a
 * user's stroke may legitimately extend slightly past the visible page
 * edge; callers that need a strictly-bounded point should call [clampToUnitSquare].
 */
data class NormalizedPoint(
    val u: Float,
    val v: Float
) {
    fun clampToUnitSquare(): NormalizedPoint =
        NormalizedPoint(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
}

/**
 * Axis-aligned bounding box of a list of normalized points. Shared by every
 * type that needs one — [SelectionPolygon] and (from Milestone M1.1)
 * `ContentBlock`'s `quad` — so the min/max-tracking logic exists in exactly
 * one place rather than being reimplemented per call site.
 */
fun List<NormalizedPoint>.boundingBox(): NormalizedRect {
    var minU = Float.POSITIVE_INFINITY
    var minV = Float.POSITIVE_INFINITY
    var maxU = Float.NEGATIVE_INFINITY
    var maxV = Float.NEGATIVE_INFINITY
    for (p in this) {
        if (p.u < minU) minU = p.u
        if (p.v < minV) minV = p.v
        if (p.u > maxU) maxU = p.u
        if (p.v > maxV) maxV = p.v
    }
    return NormalizedRect(left = minU, top = minV, right = maxU, bottom = maxV)
}

/**
 * Signed polygon area via the shoelace formula. The sign indicates winding
 * direction (which [com.aipdfreader.app.selection.engine.PolygonClipper]
 * relies on to stay correct regardless of a caller's vertex order); the
 * magnitude is the enclosed area in normalized (page-fraction) units.
 *
 * Shared by [com.aipdfreader.app.selection.geometry.PolygonBuilder]
 * (degenerate-shape rejection) and `selection.engine` (overlap-ratio
 * computation, from Milestone M1.2) — this is the one place this formula
 * is implemented; both callers use `abs()` at their own call site since
 * they care about magnitude, not winding.
 */
fun List<NormalizedPoint>.polygonArea(): Float {
    if (size < 3) return 0f
    var sum = 0f
    for (i in indices) {
        val current = this[i]
        val next = this[(i + 1) % size]
        sum += current.u * next.v - next.u * current.v
    }
    return sum / 2f
}
