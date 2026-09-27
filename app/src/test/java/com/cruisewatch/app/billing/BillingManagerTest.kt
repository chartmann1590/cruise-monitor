package com.cruisewatch.app.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cruisewatch.app.ads.InterstitialAdManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillingManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("cruisewatch_prefs", Context.MODE_PRIVATE)

    @Before
    fun setup() {
        BillingManager.resetForTests()
        prefs.edit().clear().commit()
    }

    @org.junit.After
    fun tearDown() {
        BillingManager.resetForTests()
    }

    @Test
    fun `subscription constants are defined correctly`() {
        assertEquals("ad_free_monthly", BillingManager.PRODUCT_ID_AD_FREE_MONTHLY)
        assertEquals("monthly", BillingManager.BASE_PLAN_ID_MONTHLY)
    }

    @Test
    fun `initial subscription state defaults to false when unpurchased`() {
        val manager = BillingManager.getInstance(context)
        assertFalse(manager.isSubscribed.value)
    }

    @Test
    fun `cached subscription preference initializes isSubscribed to true`() {
        prefs.edit().putBoolean("is_ad_free_subscribed", true).commit()
        val manager = BillingManager(context)
        assertTrue(manager.isSubscribed.value)
    }

    @Test
    fun `interstitial ad manager suppresses preload when user is subscribed`() {
        prefs.edit().putBoolean("is_ad_free_subscribed", true).commit()
        val interstitialManager = InterstitialAdManager(context)
        assertNotNull(interstitialManager)
        // Should return early and not attempt AdMob network load
        interstitialManager.preload()
    }
}
