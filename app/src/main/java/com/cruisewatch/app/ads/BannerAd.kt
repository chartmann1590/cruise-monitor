package com.cruisewatch.app.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.cruisewatch.app.billing.BillingManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** A standard banner ad, suppressed when the user has an active ad-free subscription. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val billingManager = remember { BillingManager.getInstance(context) }
    val isSubscribed by billingManager.isSubscribed.collectAsState()

    if (isSubscribed) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdIds.BANNER
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
