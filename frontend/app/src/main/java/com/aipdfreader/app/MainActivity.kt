package com.aipdfreader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import com.aipdfreader.app.ui.navigation.AppNavGraph
import com.aipdfreader.app.ui.theme.AiPdfReaderTheme
import com.aipdfreader.app.ui.theme.ThemeMode
import com.aipdfreader.app.ui.theme.ThemePreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themePreferences: ThemePreferences
    private val notificationOpenRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordNotificationOpen(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.mode.collectAsState()
            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AiPdfReaderTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(notificationOpenRequest = notificationOpenRequest.intValue)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordNotificationOpen(intent)
    }

    private fun recordNotificationOpen(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_NOTIFICATIONS) {
            notificationOpenRequest.intValue += 1
            intent.action = null
        }
    }

    companion object {
        const val ACTION_OPEN_NOTIFICATIONS = "com.aipdfreader.app.action.OPEN_NOTIFICATIONS"
    }
}
