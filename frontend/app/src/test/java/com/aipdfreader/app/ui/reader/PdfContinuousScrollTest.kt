package com.aipdfreader.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfContinuousScrollTest {
    @Test
    fun activePageTracksThePageMostVisibleInTheViewport() {
        val pages = listOf(
            PdfViewportPage(index = 0, offset = 0, size = 900),
            PdfViewportPage(index = 1, offset = 890, size = 900)
        )

        assertEquals(0, mostVisiblePdfPageIndex(pages, viewportStart = 0, viewportEnd = 1_000))
        assertEquals(1, mostVisiblePdfPageIndex(pages, viewportStart = 500, viewportEnd = 1_500))
    }

    @Test
    fun returnsNoActivePageWhenNothingIntersectsTheViewport() {
        assertNull(
            mostVisiblePdfPageIndex(
                pages = listOf(PdfViewportPage(index = 2, offset = 1_100, size = 400)),
                viewportStart = 0,
                viewportEnd = 1_000
            )
        )
    }
}
