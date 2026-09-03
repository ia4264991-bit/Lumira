package com.aipdfreader.app.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts real, selectable text per page using Apache PDFBox (Android port).
 *
 * This is what powers text selection: the rendered page bitmap ([PdfPageRenderer])
 * gives the visual, while this class supplies the actual selectable text shown
 * in the selection overlay beneath/around it.
 */
@Singleton
class PdfTextExtractor @Inject constructor() {

    suspend fun getPageCount(filePath: String): Int = withContext(Dispatchers.IO) {
        PDDocument.load(File(filePath)).use { doc -> doc.numberOfPages }
    }

    /** Extracts text for a single page (0-indexed). */
    suspend fun extractPageText(filePath: String, pageIndex: Int): String =
        withContext(Dispatchers.IO) {
            PDDocument.load(File(filePath)).use { document ->
                val stripper = PDFTextStripper().apply {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }
                stripper.getText(document).trim()
            }
        }

    /** Extracts text for every page in the document, in order. */
    suspend fun extractAllPages(filePath: String): List<String> = withContext(Dispatchers.IO) {
        PDDocument.load(File(filePath)).use { document ->
            (0 until document.numberOfPages).map { pageIndex ->
                val stripper = PDFTextStripper().apply {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }
                stripper.getText(document).trim()
            }
        }
    }
}
