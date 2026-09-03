package com.aipdfreader.app.selection.engine

import com.aipdfreader.app.selection.model.ContentBlock
import com.aipdfreader.app.selection.model.MatchedBlock
import com.aipdfreader.app.selection.model.SelectionPolygon
import com.aipdfreader.app.selection.model.SelectionResult
import com.aipdfreader.app.selection.model.TextContentBlock
import com.aipdfreader.app.selection.model.polygonArea
import com.aipdfreader.app.selection.textlayout.PageContentLayout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Resolves a completed lasso [SelectionPolygon] against a page's
 * [PageContentLayout] into a [SelectionResult] — the point in the Selection
 * Engine where a drawn shape actually becomes selected content.
 *
 * Two-phase, per the approved design (§6):
 * 1. **Broad phase**: [PageContentLayout.spatialIndex] narrows every block
 *    on the page down to only those whose bounding box is anywhere near the
 *    polygon's own bounding box — reused as-is from M1.1, no change.
 * 2. **Narrow phase**: [PolygonClipper] computes each candidate's exact
 *    overlap area against the polygon; a block is kept only if the overlap
 *    covers at least [SelectionEngineConfig.minOverlapRatio] of the
 *    block's own area.
 *
 * Matched blocks are then sorted into document [ReadingOrderComparator]
 * order before text is assembled, so a lasso drawn in any direction yields
 * text in reading order, not gesture order.
 *
 * Entirely independent of Compose UI, Android, and PDFBox — operates only
 * on `selection.model` and `selection.textlayout` types (both already
 * established in prior milestones; nothing here introduces a parallel
 * coordinate system, polygon representation, or spatial index).
 */
@Singleton
class SelectionEngine @Inject constructor(
    private val polygonClipper: PolygonClipper
) {

    /**
     * @param polygon the finalized lasso shape (from `selection.geometry`'s
     *   `PolygonBuilder`, M1.0), already in normalized page-display space.
     * @param layout the target page's content (from `selection.textlayout`'s
     *   `TextLayoutCache`/`PositionedTextExtractor`, M1.1).
     * @param config overlap-threshold tuning; see [SelectionEngineConfig].
     */
    fun resolve(
        polygon: SelectionPolygon,
        layout: PageContentLayout,
        config: SelectionEngineConfig = SelectionEngineConfig()
    ): SelectionResult {
        if (polygon.pageIndex != layout.pageIndex) {
            // A polygon for one page can never validly resolve against
            // another page's layout — fail safe rather than silently
            // matching against the wrong page's content.
            return SelectionResult.empty(polygon)
        }

        // Computed once and reused for both the broad-phase query and the
        // final result's boundingRect (M1.4 review: this was previously
        // computed twice per resolve() call — a redundant O(vertex count)
        // scan over the same, unchanged polygon).
        val boundingRect = polygon.boundingBox()

        val candidates = layout.spatialIndex.candidatesNear(boundingRect)

        val matches = candidates.mapNotNull { block -> matchOrNull(polygon, block, config) }
        val orderedMatches = matches.sortedWith(compareBy(ReadingOrderComparator) { it.block })

        val selectedText = orderedMatches
            .mapNotNull { (it.block as? TextContentBlock)?.text }
            .joinToString(separator = " ")

        return SelectionResult(
            pageIndex = polygon.pageIndex,
            selectedText = selectedText,
            boundingPolygon = polygon,
            boundingRect = boundingRect,
            matchedBlocks = orderedMatches
        )
    }

    /** Narrow-phase test for a single candidate block; `null` if it doesn't clear the overlap threshold. */
    private fun matchOrNull(
        polygon: SelectionPolygon,
        block: ContentBlock,
        config: SelectionEngineConfig
    ): MatchedBlock? {
        val blockArea = abs(block.quad.polygonArea())
        if (blockArea <= MIN_BLOCK_AREA) return null // degenerate extraction; nothing meaningful to overlap

        // Block quad is always convex (four rectangle corners) — the lasso
        // polygon is the arbitrary/possibly-concave one. See PolygonClipper's
        // KDoc: this argument order is the one correctness-critical detail
        // of the narrow phase.
        val overlapPolygon = polygonClipper.clip(subject = polygon.points, convexClip = block.quad)
        if (overlapPolygon.size < 3) return null // no overlap

        val overlapArea = abs(overlapPolygon.polygonArea())
        val overlapRatio = (overlapArea / blockArea).coerceIn(0f, 1f)

        return if (overlapRatio >= config.minOverlapRatio) {
            MatchedBlock(block = block, overlapRatio = overlapRatio)
        } else {
            null
        }
    }

    companion object {
        /** Skip blocks whose own quad has negligible area (malformed/degenerate extraction). */
        private const val MIN_BLOCK_AREA = 1e-8f
    }
}
