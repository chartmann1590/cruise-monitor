package com.cruisewatch.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cruisewatch.app.widget.WidgetRefreshWorker

class CruiseWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // The Mobile Ads SDK is NOT initialized here: UMP consent must be
        // gathered first. MainActivity runs ConsentManager.gatherConsent(),
        // which initializes MobileAds once ads may be requested.
        WidgetRefreshWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            getString(R.string.price_drop_channel_id),
            getString(R.string.price_drop_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
