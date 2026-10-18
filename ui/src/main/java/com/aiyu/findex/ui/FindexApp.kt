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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.model.ThemeMode
import com.aiyu.findex.ui.components.FindexDrawerSheet
import com.aiyu.findex.ui.components.PushDrawer
import com.aiyu.findex.ui.components.FindexNavRail
import com.aiyu.findex.ui.components.windowWidth
import com.aiyu.findex.ui.components.rememberPushDrawerState
import com.aiyu.findex.ui.di.failureRes
import com.aiyu.findex.ui.explorer.ExplorerScreen
import com.aiyu.findex.ui.home.HomeScreen
import com.aiyu.findex.ui.navigation.SearchRoute
import com.aiyu.findex.ui.search.SearchScreen
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
import com.aiyu.findex.ui.navigation.RecycleBinRoute
import com.aiyu.findex.ui.navigation.TabsRoute
import com.aiyu.findex.ui.tabs.TabsScreen
import com.aiyu.findex.ui.trash.RecycleBinScreen
import com.aiyu.findex.ui.onboarding.OnboardingScreen
import com.aiyu.findex.ui.theme.FindexTheme
import com.aiyu.findex.ui.util.hasNotificationAccess
import com.aiyu.findex.ui.util.hasStorageAccess
import kotlinx.coroutines.launch

/** App-wide snackbar so results of background operations show on whatever screen is visible. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun FindexApp(onReady: () -> Unit = {}, viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return // the splash stays up until settings are known
    SideEffect(onReady)

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
                val trash = (result as? OperationResult.Success)?.operation as? FileOperation.Trash
                if (trash != null) {
                    val count = trash.targets.size
                    val undo = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(R.plurals.moved_to_bin, count, count),
                        actionLabel = context.getString(R.string.undo),
                        // With an action the default is Indefinite, which would block later results.
                        duration = SnackbarDuration.Long
                    )
                    if (undo == SnackbarResult.ActionPerformed) viewModel.undoTrash(trash.id)
                    return@collect
                }
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
                val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
                FindexNavigation(onboardingDone = current.onboardingDone, onOnboardingFinished = viewModel::completeOnboarding, tabCount = tabCount)
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
private fun FindexNavigation(onboardingDone: Boolean, onOnboardingFinished: () -> Unit, tabCount: Int) {
    val navController = rememberNavController()
    val drawerState = rememberPushDrawerState()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onHome = backStackEntry?.destination?.hasRoute(HomeRoute::class) == true
    // Onboarding is the only place permissions are asked: storage is required, notifications once.
    val startDestination: Any = remember {
        val context = navController.context
        if (context.hasStorageAccess() && (onboardingDone || context.hasNotificationAccess())) HomeRoute else OnboardingRoute
    }

    // MD3 navigation by window width: modal drawer < 600dp, rail < 1200dp, standard drawer beyond.
    val width = windowWidth()
    val showNav = backStackEntry?.destination?.hasRoute(OnboardingRoute::class) != true
    val useRail = showNav && width >= 600.dp && width < 1200.dp
    val permanentDrawer = showNav && width >= 1200.dp
    val isCurrent: (Any) -> Boolean = { route -> backStackEntry?.destination?.hasRoute(route::class) == true }
    val navigate: (Any) -> Unit = { route ->
        scope.launch { drawerState.close() }
        // Home is the root: go back to it instead of stacking another copy.
        if (route == HomeRoute) navController.popBackStack(HomeRoute, inclusive = false)
        else navController.navigate(route) { launchSingleTop = true; popUpTo<HomeRoute>() }
    }
    PushDrawer(
        state = drawerState,
        permanent = permanentDrawer,
        rail = if (useRail) ({ FindexNavRail(onNavigate = navigate, tabCount = tabCount, isCurrent = isCurrent) }) else null,
        gesturesEnabled = onHome || drawerState.isOpen,
        drawerContent = {
            FindexDrawerSheet(
                tabCount = tabCount,
                isCurrent = isCurrent,
                onNavigate = navigate
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
                OnboardingScreen(onFinished = {
                    onOnboardingFinished()
                    navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                })
            }
            composable<HomeRoute> {
                HomeScreen(
                    // A permanent drawer or rail is always visible, so Home has no menu button then.
                    onOpenDrawer = if (permanentDrawer || useRail) null else ({ scope.launch { drawerState.open() } }),
                    drawerProgress = { drawerState.fraction },
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
    composable<ExplorerRoute> { ExplorerScreen(onBack = back, onOpenTabs = { navController.navigate(TabsRoute) { launchSingleTop = true } }) }
    composable<CategoryRoute> { CategoryScreen(onBack = back, onOpenFolder = openFolder) }
    composable<AnalyzerRoute> { AnalyzerScreen(onBack = back, onOpenFolder = openFolder) }
    composable<CleanerRoute> { CleanerScreen(onBack = back) }
    composable<WhatsAppRoute> { WhatsAppScreen(onBack = back) }
    composable<SettingsRoute> { SettingsScreen(onBack = back) }
    composable<RecycleBinRoute> { RecycleBinScreen(onBack = back) }
    composable<SearchRoute> { SearchScreen(onBack = back, onOpenFolder = openFolder) }
    composable<TabsRoute> {
        TabsScreen(onBack = back, onOpenExplorer = {
            // Opened from an explorer: go back to it (it follows the shared session).
            // Opened from the drawer: replace the overview so Back from the explorer returns Home.
            if (navController.previousBackStackEntry?.destination?.hasRoute(ExplorerRoute::class) == true) navController.popBackStack()
            else navController.navigate(ExplorerRoute()) { popUpTo<TabsRoute> { inclusive = true } }
        })
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisEnter(
    spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>, forward: Boolean
): EnterTransition = slideInHorizontally(spatial) { w -> if (forward) w / 10 else -w / 10 } + fadeIn(effects)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisExit(
    spatial: FiniteAnimationSpec<IntOffset>, effects: FiniteAnimationSpec<Float>, forward: Boolean
): ExitTransition = slideOutHorizontally(spatial) { w -> if (forward) -w / 10 else w / 10 } + fadeOut(effects)
