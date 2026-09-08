package com.cruisewatch.app.ui

import android.content.Context
import android.content.Intent

/** Opens the system share sheet with plain text — e.g. so someone tracking a cruise for another person can forward an alert. */
fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}
