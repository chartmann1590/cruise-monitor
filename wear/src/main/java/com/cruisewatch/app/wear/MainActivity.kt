package com.cruisewatch.app.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val repository = WearRepository()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, options)
    }

    private var onGoogleSignInResult: ((Boolean, String?) -> Unit)? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .addOnSuccessListener { onGoogleSignInResult?.invoke(true, null) }
                    .addOnFailureListener { e -> onGoogleSignInResult?.invoke(false, e.message) }
            } else {
                onGoogleSignInResult?.invoke(false, "Google sign-in didn't return a token")
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501) { // user cancelled the picker — not an error worth showing
                onGoogleSignInResult?.invoke(false, "Google sign-in failed: ${e.statusCode}")
            }
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: FCM still delivers silently on denial */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        // Data Layer fallback: pick up anything the phone already pushed before this activity ran.
        com.google.android.gms.wearable.Wearable.getDataClient(this).dataItems.addOnSuccessListener { buffer ->
            buffer.forEach { item ->
                if (item.uri.path == "/cruisewatch/summary") {
                    val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
                    DataLayerStore.update(parseDataLayerSummary(dataMap.getString("cruises"), dataMap.getString("alerts")))
                }
            }
            buffer.release()
        }

        setContent {
            MaterialTheme(colors = CruiseWatchWearColors) {
                var isSignedIn by remember { mutableStateOf(auth.currentUser != null) }
                var googleError by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    onGoogleSignInResult = { success, error ->
                        if (success) { isSignedIn = true; googleError = null } else { googleError = error }
                    }
                }
                LaunchedEffect(isSignedIn) {
                    if (isSignedIn) {
                        runCatching {
                            val token = FirebaseMessaging.getInstance().token.await()
                            repository.registerFcmToken(token)
                        }
                    }
                }
                CruiseWatchWearApp(
                    repository = repository,
                    isSignedIn = isSignedIn,
                    onSignedIn = { isSignedIn = true },
                    onGoogleSignInClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                    googleSignInError = googleError,
                )
            }
        }
    }
}

@Composable
fun CruiseWatchWearApp(
    repository: WearRepository,
    isSignedIn: Boolean,
    onSignedIn: () -> Unit,
    onGoogleSignInClick: () -> Unit = {},
    googleSignInError: String? = null,
) {
    var selectedCruiseId by remember { mutableStateOf<String?>(null) }
    val dataLayerSummary by DataLayerStore.summary.collectAsState()

    BackHandler(enabled = selectedCruiseId != null) { selectedCruiseId = null }

    WatchBackground {
        when {
            selectedCruiseId != null -> CruiseDetailScreen(
                cruiseId = selectedCruiseId!!,
                isSignedIn = isSignedIn,
                repository = repository,
                dataLayerSummary = dataLayerSummary,
                onBack = { selectedCruiseId = null },
            )
            !isSignedIn -> {
                if (dataLayerSummary != null && dataLayerSummary!!.cruises.isNotEmpty()) {
                    CruiseListScreen(
                        cruises = dataLayerSummary!!.cruises.map { it.toDisplay() },
                        alerts = dataLayerSummary!!.alerts.filter { !it.claimed }.map { it.toDisplayAlert() },
                        synced = false,
                        onCruiseClick = { selectedCruiseId = it },
                    )
                } else {
                    WearSignInScreen(
                        onSignedIn = onSignedIn,
                        onGoogleSignInClick = onGoogleSignInClick,
                        googleSignInError = googleSignInError,
                    )
                }
            }
            else -> {
                val cruises by repository.trackedCruises().collectAsState(initial = null)
                val alerts by repository.unclaimedAlerts().collectAsState(initial = emptyList())
                if (cruises == null) {
                    CenteredMessage("Loading…")
                } else {
                    CruiseListScreen(
                        cruises = cruises!!.map { it.toDisplay() },
                        alerts = alerts.map { it.toDisplayAlert() },
                        synced = true,
                        onCruiseClick = { selectedCruiseId = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.watch_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
            content()
        }
    }
}

private data class DisplayCruise(val id: String, val ship: String, val cabinCategory: String, val sailDate: String, val farePaid: Double, val currency: String, val daysLeft: Int?)
private data class DisplayAlert(val id: String, val cruiseId: String, val ship: String, val dropAmount: Double, val currentFare: Double, val claimed: Boolean)

private fun TrackedCruise.toDisplay() = DisplayCruise(id, ship, cabinCategory, sailDate, farePaid, currency, null)
private fun Alert.toDisplayAlert() = DisplayAlert(id, cruiseId, "", dropAmount, currentFare, claimed)
private fun SyncedCruise.toDisplay() = DisplayCruise(id, ship, cabinCategory, sailDate, farePaid, currency, daysLeft)
private fun SyncedAlert.toDisplayAlert() = DisplayAlert(id, cruiseId, ship, dropAmount, currentFare, claimed)

@Composable
private fun CruiseListScreen(
    cruises: List<DisplayCruise>,
    alerts: List<DisplayAlert>,
    synced: Boolean,
    onCruiseClick: (String) -> Unit,
) {
    if (cruises.isEmpty() && alerts.isEmpty()) {
        CenteredMessage("No cruises tracked yet")
        return
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sailing, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                        Text(
                            " CruiseWatch",
                            style = MaterialTheme.typography.title3,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(
                            if (synced) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                            contentDescription = null,
                            tint = if (synced) Teal else Gold,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            if (synced) " Live" else " Synced via phone",
                            style = MaterialTheme.typography.caption3,
                            color = if (synced) Teal else Gold,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
            if (alerts.isNotEmpty()) {
                item {
                    Text(
                        "Price drops",
                        style = MaterialTheme.typography.caption2,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                items(alerts) { alert ->
                    Chip(
                        onClick = { onCruiseClick(alert.cruiseId) },
                        label = { Text("🎉 $${"%.0f".format(alert.dropAmount)} off") },
                        secondaryLabel = { Text("New fare $${"%.0f".format(alert.currentFare)}") },
                        icon = { Icon(Icons.Filled.CardGiftcard, contentDescription = null) },
                        colors = ChipDefaults.chipColors(backgroundColor = Coral.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (cruises.isNotEmpty()) {
                item {
                    Text(
                        "Tracked cruises",
                        style = MaterialTheme.typography.caption2,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(cruises) { cruise ->
                    Chip(
                        onClick = { onCruiseClick(cruise.id) },
                        label = { Text(cruise.ship) },
                        secondaryLabel = {
                            Text(
                                "${cruise.currency} ${"%.0f".format(cruise.farePaid)}" +
                                    (cruise.daysLeft?.let { " · ${it}d left" } ?: ""),
                            )
                        },
                        icon = { Icon(Icons.Filled.Sailing, contentDescription = null) },
                        colors = ChipDefaults.chipColors(backgroundColor = OceanDeep.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CruiseDetailScreen(
    cruiseId: String,
    isSignedIn: Boolean,
    repository: WearRepository,
    dataLayerSummary: DataLayerSummary?,
    onBack: () -> Unit,
) {
    val history: List<DisplayAlert> = if (isSignedIn) {
        val alerts by repository.alertHistory(cruiseId).collectAsState(initial = emptyList())
        alerts.map { it.toDisplayAlert() }
    } else {
        dataLayerSummary?.alerts?.filter { it.cruiseId == cruiseId }?.map { it.toDisplayAlert() } ?: emptyList()
    }
    val shipName = if (isSignedIn) {
        val cruises by repository.trackedCruises().collectAsState(initial = emptyList())
        cruises.firstOrNull { it.id == cruiseId }?.ship
    } else {
        dataLayerSummary?.cruises?.firstOrNull { it.id == cruiseId }?.ship
    } ?: "Alert history"

    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
        item {
            Chip(
                onClick = onBack,
                label = { Text("Back") },
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.12f)),
            )
        }
        item {
            Text(
                shipName,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    "No price drops recorded yet",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        } else {
            items(history) { alert ->
                Chip(
                    onClick = {},
                    label = { Text("$${"%.0f".format(alert.dropAmount)} drop") },
                    secondaryLabel = { Text(if (alert.claimed) "Claimed" else "New fare $${"%.0f".format(alert.currentFare)}") },
                    icon = { Icon(if (alert.claimed) Icons.Filled.CheckCircle else Icons.Filled.CardGiftcard, contentDescription = null) },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (alert.claimed) Color.White.copy(alpha = 0.1f) else Coral.copy(alpha = 0.28f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.body2)
    }
}
