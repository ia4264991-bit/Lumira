package com.aipdfreader.app.selection.textlayout

import com.aipdfreader.app.data.repository.PdfRepository
import com.aipdfreader.app.selection.model.NormalizedPoint
import com.aipdfreader.app.selection.model.TextContentBlock
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts a PDF page's embedded text as word-level [TextContentBlock]s with
 * normalized bounding boxes, using Apache PDFBox (Android port) — the same
 * library and file-access pattern already used by the existing (frozen)
 * [com.aipdfreader.app.pdf.PdfTextExtractor], which this class sits
 * alongside rather than replaces: that class serves the long-press text
 * panel (a `String` of page text); this one serves the Selection Engine
 * (positioned, individually-addressable [TextContentBlock]s).
 *
 * Implements [PageContentProvider] by resolving a `pdfId` to a file path
 * through the existing, unmodified [PdfRepository] — reusing the frozen
 * repository layer read-only, exactly as
 * [com.aipdfreader.app.ui.reader.ReaderViewModel] already does, rather than
 * duplicating document lookup logic here.
 */
@Singleton
class PositionedTextExtractor @Inject constructor(
    private val pdfRepository: PdfRepository
) : PageContentProvider {

    override suspend fun getContentLayout(pdfId: Long, pageIndex: Int): PageContentLayout {
        val document = pdfRepository.getDocument(pdfId) ?: return PageContentLayout.empty(pageIndex)
        return extractPageLayout(document.filePath, pageIndex)
    }

    /**
     * Extracts [pageIndex] (0-based) from the PDF at [filePath] directly.
     * Exposed independently of [getContentLayout] so callers that already
     * have a resolved file path (or tests that don't want a `PdfRepository`
     * in play) don't need a `pdfId`.
     */
    suspend fun extractPageLayout(filePath: String, pageIndex: Int): PageContentLayout =
        withContext(Dispatchers.IO) {
            PDDocument.load(File(filePath)).use { document ->
                if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                    return@withContext PageContentLayout.empty(pageIndex)
                }

                val page = document.getPage(pageIndex)
                val rotation = page.rotation
                val mediaBox = page.mediaBox
                val isSideways = rotation == 90 || rotation == 270
                val displayWidth = if (isSideways) mediaBox.height else mediaBox.width
                val displayHeight = if (isSideways) mediaBox.width else mediaBox.height

                val collector = WordCollectingStripper(displayWidth, displayHeight)
                collector.sortByPosition = true
                collector.startPage = pageIndex + 1
                collector.endPage = pageIndex + 1
                // Triggers writeString() callbacks on `collector` for every
                // text run on the page — same entry point the existing,
                // frozen PdfTextExtractor already uses (`stripper.getText`).
                collector.getText(document)

                val blocks = collector.buildBlocks(pageIndex)
                PageContentLayout(
                    pageIndex = pageIndex,
                    blocks = blocks,
                    spatialIndex = SpatialGridIndex.build(blocks)
                )
            }
        }

    /**
     * A [PDFTextStripper] that, instead of concatenating stripped text into
     * a single `String`, collects each character's [TextPosition] and
     * regroups them into words (runs of non-whitespace characters),
     * recording each word's bounding box.
     *
     * [TextPosition]'s `xDirAdj`/`yDirAdj`/`widthDirAdj`/`heightDir` fields
     * are PDFBox's rotation-adjusted glyph coordinates: already expressed as
     * if the page were displayed right-side up, top-left origin, Y
     * increasing downward — the standard, widely-used technique for pairing
     * PDFBox text extraction with a rendered/rotated page view. Dividing by
     * the page's *displayed* width/height (already rotation-swapped by the
     * caller above) lands each word's quad directly in this app's
     * canonical normalized page-display space, with no further
     * transformation needed downstream.
     */
    private class WordCollectingStripper(
        private val displayWidthPts: Float,
        private val displayHeightPts: Float
    ) : PDFTextStripper() {

        private val words = mutableListOf<RawWord>()
        private var lineIndex = 0

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            var runText = StringBuilder()
            var runPositions = mutableListOf<TextPosition>()

            fun flush() {
                if (runText.isNotEmpty()) {
                    words += RawWord(runText.toString(), runPositions, lineIndex)
                }
                runText = StringBuilder()
                runPositions = mutableListOf()
            }

            val count = minOf(text.length, textPositions.size)
            for (i in 0 until count) {
                val character = text[i]
                if (character.isWhitespace()) {
                    flush()
                } else {
                    runText.append(character)
                    runPositions.add(textPositions[i])
                }
            }
            flush()
            lineIndex++
        }

        fun buildBlocks(pageIndex: Int): List<TextContentBlock> =
            words.mapIndexed { index, word ->
                TextContentBlock(
                    id = "p${pageIndex}_w$index",
                    text = word.text,
                    quad = word.positions.toNormalizedQuad(),
                    lineIndex = word.lineIndex,
                    readingOrderIndex = index
                )
            }

        private fun List<TextPosition>.toNormalizedQuad(): List<NormalizedPoint> {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY

            for (position in this) {
                val left = position.xDirAdj
                val bottom = position.yDirAdj
                val top = bottom - position.heightDir
                val right = left + position.widthDirAdj

                if (left < minX) minX = left
                if (top < minY) minY = top
                if (right > maxX) maxX = right
                if (bottom > maxY) maxY = bottom
            }

            val u0 = (minX / displayWidthPts).coerceIn(0f, 1f)
            val v0 = (minY / displayHeightPts).coerceIn(0f, 1f)
            val u1 = (maxX / displayWidthPts).coerceIn(0f, 1f)
            val v1 = (maxY / displayHeightPts).coerceIn(0f, 1f)

            return listOf(
                NormalizedPoint(u0, v0), // top-left
                NormalizedPoint(u1, v0), // top-right
                NormalizedPoint(u1, v1), // bottom-right
                NormalizedPoint(u0, v1)  // bottom-left
            )
        }

        private data class RawWord(
            val text: String,
            val positions: List<TextPosition>,
            val lineIndex: Int
        )
    }
}
