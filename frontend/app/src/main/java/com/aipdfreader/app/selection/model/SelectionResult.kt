package com.aipdfreader.app.selection.model

/**
 * The final output of the Selection Engine: what a completed lasso
 * gesture resolved to once matched against a page's content.
 *
 * Per the approved design (§4.1), this lives in `selection.model` alongside
 * [SelectionPolygon] and [ContentBlock] — it references only types already
 * in this package, so placing it here does not compromise the model
 * layer's zero-dependency-on-other-layers property (verified in M1.0/M1.1
 * and re-verified this milestone).
 *
 * @param boundingPolygon the exact user-drawn shape this result was
 *   resolved from — the same [SelectionPolygon] passed to the engine.
 * @param boundingRect the axis-aligned bounding rectangle of
 *   [boundingPolygon] (`boundingPolygon.boundingBox()`), provided directly
 *   on the result as a convenience so consumers don't need to recompute it.
 * @param matchedBlocks every [ContentBlock] the engine matched, in document
 *   reading order — not spatial or insertion order.
 */
data class SelectionResult(
    val pageIndex: Int,
    val selectedText: String,
    val boundingPolygon: SelectionPolygon,
    val boundingRect: NormalizedRect,
    val matchedBlocks: List<MatchedBlock>
) {
    companion object {
        /** The result of a polygon that matched no content on the page. */
        fun empty(polygon: SelectionPolygon): SelectionResult =
            SelectionResult(
                pageIndex = polygon.pageIndex,
                selectedText = "",
                boundingPolygon = polygon,
                boundingRect = polygon.boundingBox(),
                matchedBlocks = emptyList()
            )
    }
}

/**
 * One [block] the engine matched, and how much of its own area the lasso
 * covered ([overlapRatio], in `[0, 1]`) — the value
 * [com.aipdfreader.app.selection.engine.SelectionEngineConfig.minOverlapRatio]
 * was compared against to decide inclusion.
 */
data class MatchedBlock(
    val block: ContentBlock,
    val overlapRatio: Float
)
