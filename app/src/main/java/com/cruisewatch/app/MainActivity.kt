package com.cruisewatch.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cruisewatch.app.ads.InterstitialAdManager
import com.cruisewatch.app.auth.AuthViewModel
import com.cruisewatch.app.ui.CruiseWatchNavHost
import com.cruisewatch.app.ui.theme.CruiseWatchTheme
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val interstitialAdManager by lazy { InterstitialAdManager(this) }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: FCM still delivers to the notification tray on denial, just silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            authViewModel.registerFcmTokenIfAvailable(token)
        }

        interstitialAdManager.preload()

        setContent {
            CruiseWatchTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CruiseWatchNavHost(
                        authViewModel = authViewModel,
                        onCruiseAdded = { interstitialAdManager.showIfReady(this) },
                    )
                }
            }
        }
    }
}
