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
    fun nonPdfFilesUseTheExplicitExternalViewerFallback() {
        assertFalse(shouldOpenInVisionPdfReader("image/jpeg", "diagram.jpg"))
        assertFalse(shouldOpenInVisionPdfReader("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "notes.docx"))
    }

    @Test
    fun mimeTypeFallsBackToCommonFileExtensionsWhenMetadataIsGeneric() {
        assertEquals("application/msword", resolvedResourceMimeType("application/octet-stream", "lecture.doc"))
        assertEquals("application/vnd.ms-powerpoint", resolvedResourceMimeType(null, "slides.ppt"))
        assertEquals("application/vnd.ms-excel", resolvedResourceMimeType("", "marks.xls"))
        assertEquals("audio/mpeg", resolvedResourceMimeType("application/octet-stream", "recording.mp3"))
        assertEquals("video/mp4", resolvedResourceMimeType(null, "lesson.mp4"))
    }
}
