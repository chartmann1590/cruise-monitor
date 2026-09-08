package com.cruisewatch.app.ads

/**
 * Ad unit IDs. These are Google's public TEST ad unit IDs (safe to ship in
 * debug builds, always serve test creatives, never earn real revenue).
 * Replace with real ad unit IDs from your own AdMob account
 * (admob.google.com -> Apps -> CruiseWatch -> Ad units) before a Play Store
 * release, alongside the real App ID in AndroidManifest.xml. See
 * plan/03-phase2-android-app.md.
 */
object AdIds {
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
}
