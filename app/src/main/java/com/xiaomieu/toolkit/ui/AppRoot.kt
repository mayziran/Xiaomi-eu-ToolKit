package com.xiaomieu.toolkit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xiaomieu.toolkit.ui.common.MainViewModel
import com.xiaomieu.toolkit.ui.detail.DetailScreen
import com.xiaomieu.toolkit.ui.diagnostics.DiagnosticsScreen
import com.xiaomieu.toolkit.ui.features.FeaturesScreen
import com.xiaomieu.toolkit.ui.features.FeaturesViewModel
import com.xiaomieu.toolkit.ui.features.SpoofScreen
import com.xiaomieu.toolkit.ui.overview.OverviewScreen

// 枚举顺序就是底部导航的顺序。以后新增功能继续往 FEATURES 页里加，不在这里加 tab。
private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    OVERVIEW("overview", "总览", Icons.AutoMirrored.Filled.List),
    FEATURES("features", "功能", Icons.Filled.Settings),
    DIAGNOSTICS("diagnostics", "诊断", Icons.Filled.Build),
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    // Hoisted to the activity so the overview and diagnostics screens share one source of truth.
    val mainViewModel: MainViewModel = viewModel()
    // Spoof switches are shared by the 功能 page and every app detail page.
    val featuresViewModel: FeaturesViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.OVERVIEW.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.OVERVIEW.route) {
                OverviewScreen(
                    viewModel = mainViewModel,
                    onOpenDetail = { pkg -> navController.navigate("detail/$pkg") },
                )
            }
            composable(Dest.FEATURES.route) {
                FeaturesScreen(
                    mainViewModel = mainViewModel,
                    viewModel = featuresViewModel,
                    onOpenSpoof = { navController.navigate("spoof") },
                )
            }
            composable("spoof") {
                SpoofScreen(
                    mainViewModel = mainViewModel,
                    viewModel = featuresViewModel,
                    onOpenDetail = { pkg -> navController.navigate("detail/$pkg") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Dest.DIAGNOSTICS.route) {
                DiagnosticsScreen(viewModel = mainViewModel)
            }
            composable(
                route = "detail/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                DetailScreen(
                    packageName = pkg,
                    mainViewModel = mainViewModel,
                    featuresViewModel = featuresViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
