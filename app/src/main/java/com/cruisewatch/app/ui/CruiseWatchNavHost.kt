package com.cruisewatch.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cruisewatch.app.R
import com.cruisewatch.app.auth.AuthViewModel
import com.cruisewatch.app.data.CruiseRepository
import com.cruisewatch.app.data.PolicyRepository
import com.cruisewatch.app.i18n.ProvideTranslations
import com.cruisewatch.app.i18n.TranslationManager
import com.cruisewatch.app.i18n.tr
import com.cruisewatch.app.ui.screens.AddCruiseScreen
import com.cruisewatch.app.ui.screens.AlertsScreen
import com.cruisewatch.app.ui.screens.AssistantScreen
import com.cruisewatch.app.ui.screens.ClaimsScreen
import com.cruisewatch.app.ui.screens.OnboardingScreen
import com.cruisewatch.app.ui.screens.PriceHistoryScreen
import com.cruisewatch.app.ui.screens.SignInScreen
import com.cruisewatch.app.ui.screens.TrackedCruisesScreen
import com.cruisewatch.app.ui.theme.Teal
import kotlinx.coroutines.launch

private const val PREFS_NAME = "cruisewatch_prefs"
private const val KEY_ONBOARDED = "has_seen_onboarding"

private object Routes {
    const val SIGN_IN = "sign_in"
    const val CRUISES = "cruises"
    const val ADD_CRUISE = "add_cruise"
    const val PRICE_HISTORY = "price_history/{cruiseId}"
    const val ALERTS = "alerts"
    const val CLAIMS = "claims"
    const val ASSISTANT = "assistant?cruiseId={cruiseId}"

    fun priceHistory(cruiseId: String) = "price_history/$cruiseId"
    fun assistant(cruiseId: String? = null) = if (cruiseId != null) "assistant?cruiseId=$cruiseId" else "assistant"
}

private val topLevelRoutes = setOf(Routes.CRUISES, Routes.ALERTS, Routes.CLAIMS, Routes.ASSISTANT)

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
    val translationManager = remember { TranslationManager(context) }
    androidx.compose.runtime.LaunchedEffect(Unit) { translationManager.restoreSavedLanguage() }
    val policyRepository = remember { PolicyRepository(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var hasOnboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }

    androidx.compose.runtime.LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            com.cruisewatch.app.widget.WidgetUpdater.refresh(context)
            com.cruisewatch.app.wear.WearSync.pushLatest(context, repository)
        }
    }

    if (!hasOnboarded) {
        val translationState by translationManager.state.collectAsState()
        val currentLanguageCode = (translationState as? com.cruisewatch.app.i18n.TranslationState.Ready)?.language?.code ?: "en"
        OnboardingScreen(
            translationManager = translationManager,
            currentLanguageCode = currentLanguageCode,
            onFinish = {
                prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                hasOnboarded = true
            },
        )
        return
    }

    if (!isSignedIn) {
        SignInScreen(authViewModel, onGoogleSignInClick = onGoogleSignInClick)
        return
    }

    ProvideTranslations(translationManager) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        Scaffold(
            bottomBar = {
                NavigationBar(tonalElevation = 8.dp) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CRUISES,
                        onClick = { navController.navigateTopLevel(Routes.CRUISES) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text(tr(R.string.nav_cruises)) },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ALERTS,
                        onClick = { navController.navigateTopLevel(Routes.ALERTS) },
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        label = { Text(tr(R.string.nav_alerts)) },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CLAIMS,
                        onClick = { navController.navigateTopLevel(Routes.CLAIMS) },
                        icon = { Icon(Icons.Filled.Policy, contentDescription = null) },
                        label = { Text(tr(R.string.nav_policies)) },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ASSISTANT,
                        onClick = { navController.navigateTopLevel(Routes.assistant()) },
                        icon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
                        label = { Text(tr(R.string.nav_assistant)) },
                        colors = navColors(),
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.CRUISES,
                modifier = Modifier.padding(padding),
                enterTransition = { crossFadeOrSlideIn() },
                exitTransition = { crossFadeOrSlideOut() },
                popEnterTransition = { crossFadeOrSlideIn() },
                popExitTransition = { crossFadeOrSlideOut() },
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
                    PriceHistoryScreen(
                        snapshots = repository.priceHistory(cruiseId),
                        onBack = { navController.popBackStack() },
                        onAskAssistant = { navController.navigate(Routes.assistant(cruiseId)) },
                    )
                }
                composable(Routes.ALERTS) {
                    AlertsScreen(
                        alerts = repository.alerts(),
                        cruises = repository.trackedCruises(),
                        policyFor = { lineId -> policyRepository.forLine(lineId) },
                        onMarkClaimed = { alertId -> scope.launch { repository.markAlertClaimed(alertId) } },
                        onAskAssistant = { cruiseId -> navController.navigate(Routes.assistant(cruiseId)) },
                    )
                }
                composable(Routes.CLAIMS) {
                    ClaimsScreen(policies = policyRepository.all())
                }
                composable(
                    route = Routes.ASSISTANT,
                    arguments = listOf(navArgument("cruiseId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    AssistantScreen(focusCruiseId = entry.arguments?.getString("cruiseId"))
                }
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.CRUISES) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.crossFadeOrSlideIn() =
    if (targetState.destination.route in topLevelRoutes) {
        fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f)
    } else {
        slideInHorizontally(tween(280), initialOffsetX = { it / 3 }) + fadeIn(tween(280))
    }

private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.crossFadeOrSlideOut() =
    if (initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes) {
        fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f)
    } else {
        slideOutHorizontally(tween(280), targetOffsetX = { -it / 3 }) + fadeOut(tween(280))
    }

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Teal,
    selectedTextColor = Teal,
    indicatorColor = Teal.copy(alpha = 0.15f),
)
