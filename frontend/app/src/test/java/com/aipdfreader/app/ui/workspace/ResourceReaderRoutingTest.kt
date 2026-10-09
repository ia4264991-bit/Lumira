package com.aipdfreader.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceReaderRoutingTest {
    @Test
    fun pdfsUseVisionReaderEvenWhenMimeTypeIsMissingOrIncorrect() {
        assertTrue(shouldOpenInVisionPdfReader("application/pdf", "notes.bin"))
        assertTrue(shouldOpenInVisionPdfReader(null, "lecture.PDF"))
        assertTrue(shouldOpenInVisionPdfReader("APPLICATION/PDF", null))
    }

    @Test
    fun docxAndPptxUseTheInAppOfficePreviewPathInsteadOfThePdfRenderer() {
        assertFalse(shouldOpenInVisionPdfReader("image/jpeg", "diagram.jpg"))
        assertFalse(shouldOpenInVisionPdfReader("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "notes.docx"))
        assertFalse(shouldOpenInVisionPdfReader("application/vnd.openxmlformats-officedocument.presentationml.presentation", "slides.pptx"))
    }

    @Test
    fun mimeTypeFallsBackToCommonFileExtensionsWhenMetadataIsGeneric() {
        assertEquals("application/msword", resolvedResourceMimeType("application/octet-stream", "lecture.doc"))
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", resolvedResourceMimeType(null, "slides.pptx"))
        assertEquals("application/vnd.ms-excel", resolvedResourceMimeType("", "marks.xls"))
        assertEquals("audio/mpeg", resolvedResourceMimeType("application/octet-stream", "recording.mp3"))
        assertEquals("video/mp4", resolvedResourceMimeType(null, "lesson.mp4"))
    }
}
