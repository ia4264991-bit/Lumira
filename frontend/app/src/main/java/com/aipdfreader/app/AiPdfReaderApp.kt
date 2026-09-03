package com.aipdfreader.app

import android.app.Application
import com.aipdfreader.app.debug.DebugAuthBypass
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Initializes Hilt for dependency injection and PDFBox-Android, which needs
 * its resource loader initialized once before any PDF parsing happens.
 */
@HiltAndroidApp
class AiPdfReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Debug-only; no-ops entirely in release builds. See DebugAuthBypass's
        // KDoc for what this does and how to remove it once a real backend
        // is available.
        DebugAuthBypass.installIfDebug(applicationContext)
    }
}
