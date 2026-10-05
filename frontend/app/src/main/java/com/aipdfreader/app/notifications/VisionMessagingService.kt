package com.aipdfreader.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aipdfreader.app.data.repository.AuthRepository
import com.aipdfreader.app.MainActivity
import com.aipdfreader.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VisionMessagingService : FirebaseMessagingService() {
    @Inject lateinit var tokenLifecycle: PushTokenLifecycle
    @Inject lateinit var authRepository: AuthRepository

    override fun onRegistered(installationId: String) {
        tokenLifecycle.onRegistered(installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Never surface cross-account events or message content while signed out.
        if (!authRepository.isLoggedIn.value) return
        val signal = PushSignalParser.parse(message.data) ?: return
        if (!canPostNotifications()) return

        val openInbox = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOTIFICATIONS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            signal.eventId.hashCode(),
            openInbox,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vision_notification)
            .setContentTitle(getString(R.string.push_notification_title))
            .setContentText(getString(R.string.push_notification_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(signal.eventId.hashCode(), notification)
        }
    }

    private fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED)

    companion object {
        const val CHANNEL_ID = "vision_updates"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.push_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.push_notification_channel_description)
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
}
