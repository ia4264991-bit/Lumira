package com.aipdfreader.app.selection.engine

import com.aipdfreader.app.selection.model.ContentBlock
import com.aipdfreader.app.selection.model.TextContentBlock
import com.aipdfreader.app.selection.model.boundingBox

/**
 * Orders [ContentBlock]s the way a reader would encounter them on the page
 * — the ordering [SelectionEngine] uses before assembling selected text, so
 * a lasso drawn "backwards" (e.g. bottom line first) still produces text in
 * document order rather than gesture order.
 *
 * For [TextContentBlock] — the only block type anything constructs as of
 * this milestone — this uses the extractor's own `(lineIndex,
 * readingOrderIndex)`, which is the true document reading order PDFBox
 * already determined during extraction. For any other [ContentBlock]
 * subtype (`ImageContentBlock`, `HandwritingStrokeBlock` — declared in
 * M1.1, still constructed by nothing), this falls back to position-based
 * ordering (top-to-bottom, then left-to-right) via the block's own
 * bounding box.
 *
 * This fallback is what satisfies "future image, OCR, and handwriting
 * blocks can participate without redesign" (M1.2 requirement 9): the
 * moment something starts constructing those block types, they sort
 * sensibly immediately, with no change to this comparator or to
 * [SelectionEngine].
 */
object ReadingOrderComparator : Comparator<ContentBlock> {

    override fun compare(a: ContentBlock, b: ContentBlock): Int {
        val (primaryA, secondaryA) = sortKey(a)
        val (primaryB, secondaryB) = sortKey(b)

        val primaryComparison = primaryA.compareTo(primaryB)
        if (primaryComparison != 0) return primaryComparison
        return secondaryA.compareTo(secondaryB)
    }

    private fun sortKey(block: ContentBlock): Pair<Float, Float> = when (block) {
        is TextContentBlock -> block.lineIndex.toFloat() to block.readingOrderIndex.toFloat()
        else -> {
            val box = block.quad.boundingBox()
            box.top to box.left
        }
    }
}
