package com.cruisewatch.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cruisewatch.app.auth.AuthViewModel
import com.cruisewatch.app.data.CruiseRepository
import com.cruisewatch.app.data.PolicyRepository
import com.cruisewatch.app.ui.screens.AddCruiseScreen
import com.cruisewatch.app.ui.screens.AlertsScreen
import com.cruisewatch.app.ui.screens.ClaimsScreen
import com.cruisewatch.app.ui.screens.PriceHistoryScreen
import com.cruisewatch.app.ui.screens.SignInScreen
import com.cruisewatch.app.ui.screens.TrackedCruisesScreen
import kotlinx.coroutines.launch

private object Routes {
    const val SIGN_IN = "sign_in"
    const val CRUISES = "cruises"
    const val ADD_CRUISE = "add_cruise"
    const val PRICE_HISTORY = "price_history/{cruiseId}"
    const val ALERTS = "alerts"
    const val CLAIMS = "claims"

    fun priceHistory(cruiseId: String) = "price_history/$cruiseId"
}

@Composable
fun CruiseWatchNavHost(
    authViewModel: AuthViewModel,
    repository: CruiseRepository = CruiseRepository(),
    onCruiseAdded: () -> Unit = {},
    onGoogleSignInClick: () -> Unit = {},
) {
    val navController = rememberNavController()
    val isSignedIn by authViewModel.isSignedIn.collectAsState()
    val context = LocalContext.current
    val policyRepository = remember { PolicyRepository(context) }
    val scope = rememberCoroutineScope()

    if (!isSignedIn) {
        SignInScreen(authViewModel, onGoogleSignInClick = onGoogleSignInClick)
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.CRUISES,
                    onClick = { navController.navigate(Routes.CRUISES) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Cruises") },
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.ALERTS,
                    onClick = { navController.navigate(Routes.ALERTS) },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text("Alerts") },
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.CLAIMS,
                    onClick = { navController.navigate(Routes.CLAIMS) },
                    icon = { Icon(Icons.Filled.Policy, contentDescription = null) },
                    label = { Text("Policies") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CRUISES,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CRUISES) {
                TrackedCruisesScreen(
                    cruises = repository.trackedCruises(),
                    onAddCruise = { navController.navigate(Routes.ADD_CRUISE) },
                    onOpenCruise = { cruiseId -> navController.navigate(Routes.priceHistory(cruiseId)) },
                )
            }
            composable(Routes.ADD_CRUISE) {
                AddCruiseScreen(onSave = { cruise ->
                    scope.launch {
                        repository.addTrackedCruise(cruise)
                        navController.popBackStack()
                        onCruiseAdded()
                    }
                })
            }
            composable(Routes.PRICE_HISTORY) { entry ->
                val cruiseId = entry.arguments?.getString("cruiseId") ?: return@composable
                PriceHistoryScreen(snapshots = repository.priceHistory(cruiseId))
            }
            composable(Routes.ALERTS) {
                AlertsScreen(
                    alerts = repository.alerts(),
                    policyFor = { lineId -> policyRepository.forLine(lineId) },
                    onMarkClaimed = { alertId -> scope.launch { repository.markAlertClaimed(alertId) } },
                )
            }
            composable(Routes.CLAIMS) {
                ClaimsScreen(policies = policyRepository.all())
            }
        }
    }
}
