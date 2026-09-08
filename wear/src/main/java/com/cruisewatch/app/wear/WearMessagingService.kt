package com.cruisewatch.app.wear

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The scraper sends one push per registered token under users/{uid}.fcmTokens — the phone
 * and the watch each register their own, so both get the exact same price-drop notification.
 */
class WearMessagingService : FirebaseMessagingService() {
    private val repository = WearRepository()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { repository.registerFcmToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: return
        val cruiseId = message.data["cruiseId"]

        val notification = NotificationCompat.Builder(this, getString(R.string.price_drop_channel_id))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            NotificationManagerCompat.from(this).notify(cruiseId.hashCode(), notification)
        }
    }
}
