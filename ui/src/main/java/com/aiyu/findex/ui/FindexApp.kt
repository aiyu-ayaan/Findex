package com.aiyu.findex.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.model.ThemeMode
import com.aiyu.findex.ui.components.FindexDrawerSheet
import com.aiyu.findex.ui.di.failureRes
import com.aiyu.findex.ui.explorer.ExplorerScreen
import com.aiyu.findex.ui.home.HomeScreen
import com.aiyu.findex.ui.navigation.AnalyzerRoute
import com.aiyu.findex.ui.navigation.CategoryRoute
import com.aiyu.findex.ui.navigation.CleanerRoute
import com.aiyu.findex.ui.navigation.ExplorerRoute
import com.aiyu.findex.ui.navigation.SettingsRoute
import com.aiyu.findex.ui.navigation.WhatsAppRoute
import com.aiyu.findex.ui.category.CategoryScreen
import com.aiyu.findex.ui.settings.SettingsScreen
import com.aiyu.findex.ui.tools.AnalyzerScreen
import com.aiyu.findex.ui.tools.CleanerScreen
import com.aiyu.findex.ui.tools.WhatsAppScreen
import com.aiyu.findex.ui.navigation.HomeRoute
import com.aiyu.findex.ui.navigation.OnboardingRoute
import com.aiyu.findex.ui.onboarding.OnboardingScreen
import com.aiyu.findex.ui.theme.FindexTheme
import com.aiyu.findex.ui.util.hasStorageAccess
import kotlinx.coroutines.launch

/** App-wide snackbar so results of background operations show on whatever screen is visible. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun FindexApp(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return // keep the splash screen until settings are known

    val dark = when (current.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    FindexTheme(darkTheme = dark, dynamicColor = current.dynamicColor) {
        val snackbarHostState = remember { SnackbarHostState() }
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            viewModel.operationResults.collect { result ->
                val message = when (result) {
                    is OperationResult.Success -> context.getString(R.string.operation_completed)
                    is OperationResult.Cancelled -> context.getString(R.string.operation_cancelled)
                    is OperationResult.Failed -> context.getString(failureRes(result.reason))
                }
                snackbarHostState.showSnackbar(message)
            }
        }
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            Box(Modifier.fillMaxSize()) {
                FindexNavigation()
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 88.dp)
                )
            }
        }
    }
}

@Composable
private fun FindexNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onHome = backStackEntry?.destination?.hasRoute(HomeRoute::class) == true
    val startDestination: Any = remember { if (navController.context.hasStorageAccess()) HomeRoute else OnboardingRoute }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = onHome || drawerState.isOpen,
        drawerContent = {
            FindexDrawerSheet(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    if (route != HomeRoute) navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
    ) {
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { sharedAxisEnter(spatial, effects, forward = true) },
            exitTransition = { sharedAxisExit(spatial, effects, forward = true) },
            popEnterTransition = { sharedAxisEnter(spatial, effects, forward = false) },
            // Predictive back: the outgoing screen shrinks while the gesture is dragged.
            popExitTransition = { scaleOut(targetScale = 0.9f, animationSpec = effects) + fadeOut(effects) },
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(onGranted = {
                    navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                })
            }
            composable<HomeRoute> {
                HomeScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigate = { navController.navigate(it) }
                )
            }
            appDestinations(navController)
        }
    }
}

/** Feature destinations; each milestone registers its screens here. */
private fun androidx.navigation.NavGraphBuilder.appDestinations(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    val openFolder: (String) -> Unit = { navController.navigate(ExplorerRoute(it)) }
    composable<ExplorerRoute> { ExplorerScreen(onBack = back) }
    composable<CategoryRoute> { CategoryScreen(onBack = back, onOpenFolder = openFolder) }
    composable<AnalyzerRoute> { AnalyzerScreen(onBack = back, onOpenFolder = openFolder) }
    composable<CleanerRoute> { CleanerScreen(onBack = back) }
    composable<WhatsAppRoute> { WhatsAppScreen(onBack = back) }
    composable<SettingsRoute> { SettingsScreen(onBack = back) }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisEnter(
    spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>, forward: Boolean
): EnterTransition = slideInHorizontally(spatial) { w -> if (forward) w / 10 else -w / 10 } + fadeIn(effects)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisExit(
    spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>, forward: Boolean
): ExitTransition = slideOutHorizontally(spatial) { w -> if (forward) -w / 10 else w / 10 } + fadeOut(effects)
