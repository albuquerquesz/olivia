package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.olivia.companion.ActiveBackend

private enum class OliviaDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Início", Icons.Outlined.Home),
    RECORD("record", "Gravar", Icons.Outlined.Add),
    SESSIONS("sessions", "Sessões", Icons.Outlined.History),
}

private const val SETTINGS_ROUTE = "settings"

@Composable
fun OliviaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = OliviaDestination.entries.any { it.route == currentRoute }
    val travelPx = with(LocalDensity.current) { OliviaMotion.travel.roundToPx() }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                OliviaBottomBar(currentRoute) { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(OliviaDestination.HOME.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = OliviaDestination.HOME.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            enterTransition = {
                fadeIn(OliviaMotion.navigation()) +
                    slideInVertically(OliviaMotion.navigation()) { travelPx }
            },
            exitTransition = {
                fadeOut(OliviaMotion.navigation()) +
                    slideOutVertically(OliviaMotion.navigation()) { -travelPx }
            },
            popEnterTransition = {
                fadeIn(OliviaMotion.navigation()) +
                    slideInVertically(OliviaMotion.navigation()) { -travelPx }
            },
            popExitTransition = {
                fadeOut(OliviaMotion.navigation()) +
                    slideOutVertically(OliviaMotion.navigation()) { travelPx }
            },
        ) {
            composable(OliviaDestination.HOME.route) {
                OliviaHomeScreen(backend = ActiveBackend.current)
            }
            composable(OliviaDestination.RECORD.route) {
                OliviaRecordingScreen(
                    backend = ActiveBackend.current,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(OliviaDestination.SESSIONS.route) { OliviaSessionsScreen() }
            composable(SETTINGS_ROUTE) {
                OliviaSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun OliviaBottomBar(
    currentRoute: String?,
    onDestinationSelected: (OliviaDestination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(top = 36.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup()
                .padding(horizontal = 16.dp),
        ) {
            OliviaDestination.entries.forEach { destination ->
                OliviaNavigationItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onDestinationSelected(destination) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun OliviaNavigationItem(
    destination: OliviaDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecord = destination == OliviaDestination.RECORD
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        OliviaMotion.press(),
        label = "navigation press",
    )
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        OliviaMotion.selection(),
        label = "navigation selection",
    )
    val indicatorAlpha by animateFloatAsState(
        if (selected) 1f else 0f,
        OliviaMotion.selection(),
        label = "navigation indicator",
    )

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.BottomCenter) {
            if (isRecord) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        destination.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            } else {
                Icon(
                    destination.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                )
            }
        }
        Text(
            destination.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(
            Modifier.width(16.dp).height(2.dp)
                .graphicsLayer { alpha = indicatorAlpha }
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}
