package com.olivia.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.olivia.companion.ActiveBackend

private enum class OliviaDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Início", Icons.Filled.Home),
    RECORD("record", "Gravar", Icons.Filled.Add),
    SESSIONS("sessions", "Sessões", Icons.Filled.History),
}

private const val SETTINGS_ROUTE = "settings"

@Composable
fun OliviaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = OliviaDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                OliviaBottomBar(
                    currentRoute = currentRoute,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(OliviaDestination.HOME.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = OliviaDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(OliviaDestination.HOME.route) {
                OliviaHomeScreen(
                    backend = ActiveBackend.current,
                )
            }
            composable(OliviaDestination.RECORD.route) {
                OliviaRecordingScreen(
                    backend = ActiveBackend.current,
                    onOpenSettings = {
                        navController.navigate(SETTINGS_ROUTE)
                    },
                )
            }
            composable(OliviaDestination.SESSIONS.route) {
                OliviaSessionsScreen()
            }
            composable(SETTINGS_ROUTE) {
                OliviaSettingsScreen()
            }
        }
    }
}

@Composable
private fun OliviaBottomBar(
    currentRoute: String?,
    onDestinationSelected: (OliviaDestination) -> Unit,
) {
    NavigationBar {
        OliviaDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    if (destination == OliviaDestination.RECORD) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-20).dp)
                                .zIndex(1f)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(percent = 50),
                                )
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                tint = Color.White,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                        )
                    }
                },
                label = { Text(destination.label) },
                colors = if (destination == OliviaDestination.RECORD) {
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.White,
                        indicatorColor = Color.Transparent,
                    )
                } else {
                    NavigationBarItemDefaults.colors()
                },
            )
        }
    }
}
