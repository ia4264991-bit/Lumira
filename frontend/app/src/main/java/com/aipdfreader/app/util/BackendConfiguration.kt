package com.aipdfreader.app.util

import com.aipdfreader.app.BuildConfig
import android.os.Build

/** Release still has an example URL until Vision's API is deployed. */
object BackendConfiguration {
    val isConfigured: Boolean
        get() = runCatching {
            val host = java.net.URI(BuildConfig.BACKEND_BASE_URL).host.orEmpty()
            val emulator = Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
                Build.HARDWARE in setOf("goldfish", "ranchu", "vbox86") ||
                Build.PRODUCT.startsWith("sdk", ignoreCase = true)
            val emulatorOnlyDebugHost = BuildConfig.DEBUG && host == "10.0.2.2" && !emulator
            host.isNotBlank() && !host.endsWith(".example.com", ignoreCase = true) && !emulatorOnlyDebugHost
        }.getOrDefault(false)
}
