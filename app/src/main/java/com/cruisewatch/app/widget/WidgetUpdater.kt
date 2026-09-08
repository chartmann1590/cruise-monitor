package com.cruisewatch.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Call after anything that changes what the home screen widget should show: sign-in, a new cruise, an FCM push. */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        CruiseWidget().updateAll(context)
    }
}
