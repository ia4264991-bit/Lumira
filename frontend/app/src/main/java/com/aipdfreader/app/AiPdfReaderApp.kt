package com.aipdfreader.app

import android.app.Application
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
    }
}
