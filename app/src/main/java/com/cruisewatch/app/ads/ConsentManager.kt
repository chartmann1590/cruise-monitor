package com.cruisewatch.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google User Messaging Platform (UMP) consent flow.
 *
 * Follows Google's recommended sequence: request a consent info update on
 * app start, load and show a consent form if one is required, and only
 * initialize the Google Mobile Ads SDK once consent gathering has completed
 * and [com.google.android.ump.ConsentInformation.canRequestAds] allows it.
 * In regions where no form is required (e.g. the US) nothing is shown and
 * ads initialize straight away, so the app is never blocked on consent.
 * Ad loads (banner, interstitial) are likewise gated on [canRequestAds].
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    private val mobileAdsInitializeCalled = AtomicBoolean(false)

    /** True when the stored consent state already allows requesting ads. */
    fun canRequestAds(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context).canRequestAds()

    /**
     * Runs the consent flow from the main activity. Safe to call on every
     * activity start: UMP no-ops when consent was already gathered and no
     * form is required, and [initializeMobileAdsOnce] is idempotent.
     * [onAdsReady] fires whenever this call determines ads may be requested
     * (immediately for returning users, after the form otherwise).
     */
    fun gatherConsent(activity: Activity, onAdsReady: () -> Unit = {}) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated: show the form if this user needs one.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsOnce(activity.applicationContext)
                        onAdsReady()
                    }
                }
            },
            { requestError ->
                // Consent info update failed (e.g. offline). Fall back to the
                // consent state gathered by a previous session, if any.
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                if (consentInformation.canRequestAds()) {
                    initializeMobileAdsOnce(activity.applicationContext)
                    onAdsReady()
                }
            },
        )
        // Consent was gathered in a previous session and still allows ads:
        // initialize now without waiting on the network round-trip.
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsOnce(activity.applicationContext)
            onAdsReady()
        }
    }

    private fun initializeMobileAdsOnce(context: Context) {
        if (mobileAdsInitializeCalled.getAndSet(true)) return
        MobileAds.initialize(context)
    }
}
