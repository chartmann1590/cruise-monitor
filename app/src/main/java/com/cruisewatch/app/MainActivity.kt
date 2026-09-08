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
import com.cruisewatch.app.data.CruiseRepository
import com.cruisewatch.app.ui.CruiseWatchNavHost
import com.cruisewatch.app.ui.theme.CruiseWatchTheme
import com.cruisewatch.app.wear.WearSync
import com.cruisewatch.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val interstitialAdManager by lazy { InterstitialAdManager(this) }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, options)
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: FCM still delivers to the notification tray on denial, just silently */ }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                authViewModel.signInWithGoogle(idToken)
            } else {
                authViewModel.setGoogleSignInError("Google sign-in didn't return a token")
            }
        } catch (e: ApiException) {
            // Status code 12501 is the user cancelling the picker — not an error worth showing.
            if (e.statusCode != 12501) {
                authViewModel.setGoogleSignInError("Google sign-in failed: ${e.statusCode}")
            }
        }
    }

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
                        onCruiseAdded = {
                            interstitialAdManager.showIfReady(this)
                            CoroutineScope(Dispatchers.IO).launch {
                                WidgetUpdater.refresh(applicationContext)
                                WearSync.pushLatest(applicationContext, CruiseRepository())
                            }
                        },
                        onGoogleSignInClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                    )
                }
            }
        }
    }
}
