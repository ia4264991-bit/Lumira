package com.aipdfreader.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.aipdfreader.app.data.sync.LocalCardSyncScheduler
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
    @Inject lateinit var localCardSyncScheduler: LocalCardSyncScheduler
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        VisionMessagingService.createNotificationChannel(this)
        pushTokenLifecycle.start()
        runCatching {
            val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Handler(Looper.getMainLooper()).postDelayed({ localCardSyncScheduler.enqueue() }, 1_000L)
                }
            }
            manager.registerDefaultNetworkCallback(callback)
            connectivityManager = manager
            networkCallback = callback
        }
    }

    override fun onTerminate() {
        networkCallback?.let { callback -> runCatching { connectivityManager?.unregisterNetworkCallback(callback) } }
        super.onTerminate()
    }
}
