package dev.blipwave.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.blipwave.ui.chat.ChatScreen
import dev.blipwave.ui.connections.ConnectionsScreen
import dev.blipwave.ui.device.DeviceDetailScreen
import dev.blipwave.ui.scan.ScanScreen
import dev.blipwave.ui.settings.SettingsScreen

private const val TRANSITION_MILLIS = 320

@Composable
fun BlipwaveNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTop = TopDestination.entries.firstOrNull { it.route == backStackEntry?.destination?.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Alt menü yalnızca üst seviye ekranlarda görünür; detayda aşağı kayarak kaybolur
            AnimatedVisibility(
                visible = currentTop != null,
                enter = slideInVertically(tween(TRANSITION_MILLIS)) { it } + fadeIn(),
                exit = slideOutVertically(tween(TRANSITION_MILLIS)) { it } + fadeOut(),
            ) {
                BlipBottomBar(current = currentTop, onSelect = { navController.navigateToTop(it) })
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SCAN,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            // Sekmeler arası: yumuşak fade + hafif büyüme
            enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) + scaleIn(tween(TRANSITION_MILLIS), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(TRANSITION_MILLIS / 2)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
            popExitTransition = { fadeOut(tween(TRANSITION_MILLIS / 2)) },
        ) {
            composable(Routes.SCAN) {
                ScanScreen(onOpenDevice = { navController.navigate(Routes.device(it.address, it.name)) })
            }
            composable(Routes.CONNECTIONS) {
                ConnectionsScreen(
                    onOpenDevice = { address, name -> navController.navigate(Routes.device(address, name)) },
                    onGoToScan = { navController.navigateToTop(TopDestination.SCAN) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }

            // Detay ve sohbet: sağdan kayarak gelir, geri dönüşte sağa kayarak çıkar
            composable(
                route = Routes.DEVICE,
                arguments = listOf(
                    navArgument(Routes.ARG_ADDRESS) { type = NavType.StringType },
                    navArgument(Routes.ARG_NAME) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
                enterTransition = { slideInHorizontally(tween(TRANSITION_MILLIS)) { it / 3 } + fadeIn(tween(TRANSITION_MILLIS)) },
                popExitTransition = { slideOutHorizontally(tween(TRANSITION_MILLIS)) { it / 3 } + fadeOut(tween(TRANSITION_MILLIS)) },
            ) {
                DeviceDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConsole = { address -> navController.navigate(Routes.chat(address)) },
                )
            }
            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument(Routes.ARG_ADDRESS) { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(tween(TRANSITION_MILLIS)) { it / 3 } + fadeIn(tween(TRANSITION_MILLIS)) },
                popExitTransition = { slideOutHorizontally(tween(TRANSITION_MILLIS)) { it / 3 } + fadeOut(tween(TRANSITION_MILLIS)) },
            ) {
                ChatScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTop(destination: TopDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
