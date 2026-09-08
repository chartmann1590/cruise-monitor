package com.cruisewatch.app.wear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class CruiseWatchWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            getString(R.string.price_drop_channel_id),
            getString(R.string.price_drop_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
