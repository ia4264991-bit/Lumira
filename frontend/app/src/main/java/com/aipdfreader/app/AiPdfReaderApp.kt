package com.aipdfreader.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.aipdfreader.app.notifications.PushTokenLifecycle
import com.aipdfreader.app.notifications.VisionMessagingService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Initializes Hilt for dependency injection and PDFBox-Android, which needs
 * its resource loader initialized once before any PDF parsing happens.
 */
@HiltAndroidApp
class AiPdfReaderApp : Application() {
    @Inject lateinit var pushTokenLifecycle: PushTokenLifecycle

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        VisionMessagingService.createNotificationChannel(this)
        pushTokenLifecycle.start()
    }
}
