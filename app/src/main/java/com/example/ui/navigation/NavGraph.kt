package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.machine.MachineDetailsScreen
import com.example.ui.screens.notifications.NotificationsScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.viewmodel.WashViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.LocalLaundryService)
    object History : Screen("history", "History", Icons.Default.History)
    object Machine : Screen("machine", "Hardware", Icons.Default.Sensors)
    object Notifications : Screen("notifications", "Alerts", Icons.Default.Notifications)
    object Profile : Screen("profile", "Settings", Icons.Default.Settings)
}

val navItems = listOf(
    Screen.Dashboard,
    Screen.History,
    Screen.Machine,
    Screen.Notifications,
    Screen.Profile
)

@Composable
fun MainAppScaffold(
    viewModel: WashViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                navItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            if (screen == Screen.Notifications && unreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = CyanPrimary) {
                                            Text(unreadCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(viewModel = viewModel)
                }
                composable(Screen.History.route) {
                    HistoryScreen(viewModel = viewModel)
                }
                composable(Screen.Machine.route) {
                    MachineDetailsScreen(viewModel = viewModel)
                }
                composable(Screen.Notifications.route) {
                    NotificationsScreen(viewModel = viewModel)
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(viewModel = viewModel)
                }
            }
        }
    }
}
