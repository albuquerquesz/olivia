package com.olivia.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.olivia.companion.session.OliviaSessionViewModel
import com.olivia.core.SessionId

private enum class OliviaDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Início", Icons.Outlined.Home),
    RECORD("record", "Gravar", Icons.Outlined.Add),
    SESSIONS("sessions", "Sessões", Icons.Outlined.History),
}

private const val WELCOME_ROUTE = "welcome"
private const val SETTINGS_ROUTE = "settings"
private const val TAKE_ROUTE = "take/{sessionId}"

@Composable
fun OliviaApp(
    viewModel: OliviaSessionViewModel = viewModel(
        factory = OliviaSessionViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = OliviaDestination.entries.any { it.route == currentRoute }
    val travelPx = with(LocalDensity.current) { OliviaMotion.travel.roundToPx() }

    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = WELCOME_ROUTE,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showBottomBar) 120.dp else 0.dp),
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
            composable(WELCOME_ROUTE) {
                OliviaWelcomeScreen(
                    onGoogleLogin = {
                        navController.navigate(OliviaDestination.HOME.route) {
                            popUpTo(WELCOME_ROUTE) { inclusive = true }
                        }
                    },
                )
            }
            composable(OliviaDestination.HOME.route) {
                OliviaHomeScreen(uiState)
            }
            composable(OliviaDestination.RECORD.route) {
                OliviaRecordingScreen(
                    state = uiState,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onAudioMode = viewModel::setAudioMode,
                    onStart = viewModel::start,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onDegrade = viewModel::degrade,
                    onRecover = viewModel::recover,
                    onStop = viewModel::stop,
                )
            }
            composable(OliviaDestination.SESSIONS.route) {
                OliviaSessionsScreen(uiState.takes) { sessionId ->
                    navController.navigate("take/${sessionId.value}")
                }
            }
            composable(
                TAKE_ROUTE,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                val sessionId = SessionId(entry.arguments?.getString("sessionId").orEmpty())
                val take = viewModel.take(sessionId)
                if (take == null) {
                    OliviaSessionsScreen(uiState.takes) { id ->
                        navController.navigate("take/${id.value}")
                    }
                } else {
                    OliviaTakeScreen(take, onNavigateBack = { navController.popBackStack() })
                }
            }
            composable(SETTINGS_ROUTE) {
                OliviaSettingsScreen(
                    audioMode = uiState.audioMode,
                    live = uiState.isLive,
                    onAudioMode = viewModel::setAudioMode,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            }
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OliviaBottomBar(
    currentRoute: String?,
    onDestinationSelected: (OliviaDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup()
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
    }
}
