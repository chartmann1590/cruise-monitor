package com.cruisewatch.app.fcm

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cruisewatch.app.MainActivity
import com.cruisewatch.app.R
import com.cruisewatch.app.data.CruiseRepository
import com.cruisewatch.app.wear.WearSync
import com.cruisewatch.app.widget.WidgetUpdater
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the price-drop pushes sent by scraper/src/fcm.ts. */
class CruiseWatchMessagingService : FirebaseMessagingService() {
    private val repository = CruiseRepository()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { repository.registerFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: return
        val cruiseId = message.data["cruiseId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("cruiseId", cruiseId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, getString(R.string.price_drop_channel_id))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            NotificationManagerCompat.from(this).notify(cruiseId.hashCode(), notification)
        }

        // A price drop is exactly the moment the widget and watch should refresh, not just when the app is open.
        scope.launch {
            runCatching { WidgetUpdater.refresh(applicationContext) }
            runCatching { WearSync.pushLatest(applicationContext, repository) }
        }
    }
}
