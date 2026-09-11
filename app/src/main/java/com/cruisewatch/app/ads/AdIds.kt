package com.cruisewatch.app.ads

import com.cruisewatch.app.BuildConfig

/**
 * Ad unit IDs, resolved at build time from a CI secret or local.properties
 * (never hardcoded — see ADMOB_* handling in app/build.gradle.kts). Falls
 * back to Google's public TEST ad unit IDs if neither is set, so the app
 * always builds and runs but only serves real ads with real IDs configured.
 */
object AdIds {
    val BANNER = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    val INTERSTITIAL = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID
}
