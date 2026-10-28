package com.example.woodsreader.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.woodsreader.ui.library.LibraryScreen
import com.example.woodsreader.ui.reader.ReaderScreen
import com.example.woodsreader.ui.search.SearchScreen
import com.example.woodsreader.ui.stats.ReadingStatsScreen
import com.example.woodsreader.ui.settings.SettingsScreen
import com.example.woodsreader.ui.onboarding.OnboardingScreen
import com.example.woodsreader.ui.bookdetail.BookDetailScreen
import com.example.woodsreader.ui.graph.GraphScreen
import com.example.woodsreader.ui.graph.CandidatesScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Destinos de navegación ──────────────────────────────────────
sealed class Screen(val route: String) {
    object Library  : Screen("library")
    object Reader   : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
    object BookDetail : Screen("book_detail/{bookId}") {
        fun createRoute(bookId: String) = "book_detail/$bookId"
    }
    object Graph : Screen("graph/{bookId}?chapterIndex={chapterIndex}") {
        fun createRoute(bookId: String, chapterIndex: Int = -1) = "graph/$bookId?chapterIndex=$chapterIndex"
    }
    object Candidates : Screen("graph/{bookId}/candidates") {
        fun createRoute(bookId: String) = "graph/$bookId/candidates"
    }
    object Search   : Screen("search")
    object Stats    : Screen("stats")
    object Settings : Screen("settings")
    object Onboarding : Screen("onboarding")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LectorNavHost(
    hasCompletedOnboarding: Boolean,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    SharedTransitionLayout {
        NavHost(
        navController    = navController,
        startDestination = if (hasCompletedOnboarding) Screen.Library.route else Screen.Onboarding.route,
        modifier         = modifier,
        enterTransition = {
            if (targetState.destination.route == Screen.Reader.route) {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(250))
            } else {
                fadeIn(tween(200))
            }
        },
        exitTransition = {
            if (initialState.destination.route == Screen.Reader.route) {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(250))
            } else {
                fadeOut(tween(200))
            }
        },
        popEnterTransition = {
            if (targetState.destination.route == Screen.Reader.route) {
                slideInHorizontally(initialOffsetX = { -it }) + fadeIn(tween(250))
            } else {
                fadeIn(tween(200))
            }
        },
        popExitTransition = {
            if (initialState.destination.route == Screen.Reader.route) {
                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(tween(250))
            } else {
                fadeOut(tween(200))
            }
        }
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                onBookClick = { navController.navigate(Screen.BookDetail.createRoute(it)) },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this@composable
            )
        }
        composable(
            route = Screen.BookDetail.route,
            arguments = listOf(androidx.navigation.navArgument("bookId") {
                type = androidx.navigation.NavType.StringType
            }),
            enterTransition = { slideInHorizontally(animationSpec = tween(350)) { it / 2 } + fadeIn(tween(350)) },
            exitTransition  = { slideOutHorizontally(animationSpec = tween(350)) { it / 2 } + fadeOut(tween(350)) },
            popEnterTransition  = { slideInHorizontally(animationSpec = tween(350)) { -it / 2 } + fadeIn(tween(350)) },
            popExitTransition   = { slideOutHorizontally(animationSpec = tween(350)) { it / 2 } + fadeOut(tween(350)) }
        ) {
            BookDetailScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenReader = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId)) {
                        popUpTo(Screen.Library.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Reader.route) { backStackEntry ->
            ReaderScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigateToGraph = { bookId, chapterIndex ->
                    navController.navigate(Screen.Graph.createRoute(bookId, chapterIndex))
                },
                targetLocatorJson = backStackEntry.savedStateHandle.getStateFlow<String?>("target_locator_json", null).collectAsStateWithLifecycle().value,
                onClearTargetLocator = { backStackEntry.savedStateHandle.remove<String>("target_locator_json") },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this@composable
            )
        }
        composable(
            route = Screen.Graph.route,
            arguments = listOf(
                androidx.navigation.navArgument("bookId") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("chapterIndex") {
                    type = androidx.navigation.NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            GraphScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigateToCandidates = {
                    navController.navigate(Screen.Candidates.createRoute(bookId))
                },
                onJumpToLocator = { locator ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        "target_locator_json",
                        locator.toJSON().toString()
                    )
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Screen.Candidates.route,
            arguments = listOf(
                androidx.navigation.navArgument("bookId") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) {
            CandidatesScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onBookClick = { navController.navigate(Screen.BookDetail.createRoute(it)) },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Stats.route) {
            ReadingStatsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingFinished = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        }
    }
}
