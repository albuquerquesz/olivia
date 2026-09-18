package com.olivia.companion.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.olivia.companion.ActiveBackend
import com.olivia.companion.session.OliviaSessionViewModel
import com.olivia.companion.session.TakeSummary
import com.olivia.core.RecordingAsset
import com.olivia.core.SessionId
import com.olivia.core.SessionState

private enum class OliviaDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Início", Icons.Outlined.Home),
    RECORD("record", "Gravar", Icons.Outlined.Add),
    SESSIONS("sessions", "Takes", Icons.Outlined.History),
    SETTINGS("settings", "Ajustes", Icons.Outlined.Settings),
}

private val OliviaSideDestinations = listOf(
    OliviaDestination.HOME,
    OliviaDestination.SESSIONS,
    OliviaDestination.SETTINGS,
)

private const val WELCOME_ROUTE = "welcome"
private const val TAKE_ROUTE = "take/{sessionId}"

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasBluetoothPermission(context: Context, required: Boolean): Boolean {
    if (!required || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return listOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
    ).all { hasPermission(context, it) }
}

private fun bottomBarPadding(showBottomBar: Boolean) =
    80.dp.takeIf { showBottomBar } ?: 0.dp

private fun pressScale(pressed: Boolean): Float {
    if (pressed) return 0.94f
    return 1f
}

@Composable
private fun navigationColor(selected: Boolean) =
    MaterialTheme.colorScheme.onSurface.takeIf { selected }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun OliviaApp(
    viewModel: OliviaSessionViewModel = viewModel(
        factory = OliviaSessionViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application,
        ),
    ),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bluetoothPermissionRequired = ActiveBackend.current.usesDatSdk
    var audioPermissionGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }
    var bluetoothPermissionGranted by remember(bluetoothPermissionRequired) {
        mutableStateOf(hasBluetoothPermission(context, bluetoothPermissionRequired))
    }
    val completeOnboarding = {
        navController.navigate(OliviaDestination.HOME.route) {
            popUpTo(WELCOME_ROUTE) { inclusive = true }
        }
    }
    val onboardingPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        audioPermissionGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        bluetoothPermissionGranted = hasBluetoothPermission(context, bluetoothPermissionRequired)
    }
    val showBottomBar = OliviaDestination.entries.any { it.route == currentRoute }
    val travelPx = with(LocalDensity.current) { OliviaMotion.travel.roundToPx() }
    val window = (context as? Activity)?.window
    val systemBarColor = MaterialTheme.colorScheme.background

    SideEffect {
        window?.let {
            it.statusBarColor = systemBarColor.toArgb()
            it.navigationBarColor = systemBarColor.toArgb()
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    val requestAudioPermission = {
        if (!audioPermissionGranted) {
            onboardingPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
    val requestBluetoothPermission = {
        if (bluetoothPermissionRequired) {
            val missingPermissions = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            ).filterNot { hasPermission(context, it) }
            if (missingPermissions.isNotEmpty()) {
                onboardingPermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }

    DisposableEffect(lifecycleOwner, currentRoute, audioPermissionGranted) {
        if (!audioPermissionGranted || currentRoute == WELCOME_ROUTE) {
            viewModel.stopVoiceCommands()
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.startVoiceCommands()
                    Lifecycle.Event.ON_PAUSE -> viewModel.stopVoiceCommands()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                viewModel.startVoiceCommands()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.stopVoiceCommands()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = WELCOME_ROUTE,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomBarPadding(showBottomBar)),
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
                    audioPermissionGranted = audioPermissionGranted,
                    bluetoothPermissionRequired = bluetoothPermissionRequired,
                    bluetoothPermissionGranted = bluetoothPermissionGranted,
                    onRequestAudioPermission = requestAudioPermission,
                    onRequestBluetoothPermission = requestBluetoothPermission,
                    onEnter = completeOnboarding,
                )
            }
            composable(OliviaDestination.HOME.route) {
                OliviaHomeScreen(
                    state = uiState,
                    onSeeAllTakes = {
                        navController.navigate(OliviaDestination.SESSIONS.route) {
                            popUpTo(OliviaDestination.HOME.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenRecord = {
                        navController.navigate(OliviaDestination.RECORD.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(OliviaDestination.SETTINGS.route) {
                            popUpTo(OliviaDestination.HOME.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenTake = { sessionId ->
                        navController.navigate("take/${sessionId.value}")
                    },
                )
            }
            composable(OliviaDestination.RECORD.route) {
                OliviaRecordingScreen(
                    state = uiState,
                    onNavigateBack = { navController.popBackStack() },
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
                TakeRouteContent(
                    take = viewModel.take(sessionId),
                    takes = uiState.takes,
                    exportState = uiState.exportState,
                    onExport = { viewModel.exportTake(sessionId) },
                    onOpenTake = { id -> navController.navigate("take/${id.value}") },
                    onNavigateBack = { navController.popBackStack() },
                    recordingPathResolver = viewModel::localRecordingPath,
                )
            }
            composable(OliviaDestination.SETTINGS.route) {
                OliviaSettingsScreen(
                    audioMode = uiState.audioMode,
                    live = uiState.isLive,
                    onAudioMode = viewModel::setAudioMode,
                )
            }
            }
            if (showBottomBar) {
                OliviaBottomBar(
                    currentRoute = currentRoute,
                    showNavigation = currentRoute != OliviaDestination.RECORD.route,
                    isLive = uiState.isLive,
                    isPaused = uiState.session?.state == SessionState.PAUSED,
                    onRecordClick = {
                        if (currentRoute == OliviaDestination.RECORD.route) {
                            viewModel.start()
                        } else {
                            navController.navigate(OliviaDestination.RECORD.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = viewModel::stop,
                    onDestinationSelected = { destination ->
                        val returningFromRecord =
                            destination == OliviaDestination.HOME &&
                                currentRoute == OliviaDestination.RECORD.route
                        if (returningFromRecord) {
                            navController.popBackStack(OliviaDestination.HOME.route, inclusive = false)
                        }
                        if (!returningFromRecord) {
                            navController.navigate(destination.route) {
                                popUpTo(OliviaDestination.HOME.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .offset(y = 12.dp)
                        .padding(start = 16.dp, end = 16.dp)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TakeRouteContent(
    take: TakeSummary?,
    takes: List<TakeSummary>,
    exportState: com.olivia.companion.session.ExportState,
    onExport: () -> Unit,
    onOpenTake: (SessionId) -> Unit,
    onNavigateBack: () -> Unit,
    recordingPathResolver: suspend (RecordingAsset) -> String?,
) {
    if (take != null) {
        OliviaTakeScreen(
            take = take,
            onNavigateBack = onNavigateBack,
            exportState = exportState,
            onExport = onExport,
            recordingPathResolver = recordingPathResolver,
        )
        return
    }
    OliviaSessionsScreen(takes, onOpenTake)
}

@Composable
private fun OliviaBottomBar(
    currentRoute: String?,
    showNavigation: Boolean,
    isLive: Boolean,
    isPaused: Boolean,
    onDestinationSelected: (OliviaDestination) -> Unit,
    onRecordClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = when {
            showNavigation && isLive -> Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            showNavigation -> Arrangement.spacedBy(12.dp)
            isLive -> Arrangement.spacedBy(8.dp, Alignment.End)
            else -> Arrangement.End
        },
    ) {
        AnimatedVisibility(
            visible = showNavigation && !isLive,
            modifier = if (showNavigation) Modifier.weight(1f) else Modifier,
            enter = fadeIn(OliviaMotion.footer()) + expandHorizontally(
                animationSpec = OliviaMotion.footer(),
                expandFrom = Alignment.Start,
            ),
            exit = fadeOut(OliviaMotion.footer()) + shrinkHorizontally(
                animationSpec = OliviaMotion.footer(),
                shrinkTowards = Alignment.Start,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OliviaSideDestinations.forEach { destination ->
                        OliviaNavigationItem(
                            destination = destination,
                            selected = currentRoute == destination.route,
                            enabled = !isLive,
                            onClick = { onDestinationSelected(destination) },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = isLive,
            enter = fadeIn(OliviaMotion.footer()) + expandHorizontally(
                animationSpec = OliviaMotion.footer(),
                expandFrom = Alignment.Start,
            ),
            exit = fadeOut(OliviaMotion.footer()) + shrinkHorizontally(
                animationSpec = OliviaMotion.footer(),
                shrinkTowards = Alignment.Start,
            ),
        ) {
            OliviaPauseButton(
                isPaused = isPaused,
                onClick = if (isPaused) onResume else onPause,
                modifier = Modifier.size(56.dp),
            )
        }
        OliviaRecordButton(
            live = isLive,
            selected = !isLive && currentRoute == OliviaDestination.RECORD.route,
            onClick = if (isLive) onStop else onRecordClick,
            modifier = Modifier.size(if (isLive) 56.dp else 72.dp),
        )
    }
}

@Composable
private fun OliviaNavigationItem(
    destination: OliviaDestination,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        pressScale(pressed),
        OliviaMotion.press(),
        label = "navigation press",
    )
    val color by animateColorAsState(
        navigationColor(selected),
        OliviaMotion.selection(),
        label = "navigation selection",
    )

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else color,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
        Text(
            destination.label,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OliviaPauseButton(
    isPaused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        pressScale(pressed),
        OliviaMotion.press(),
        label = "pause press",
    )
    val description = if (isPaused) "Retomar gravação" else "Pausar gravação"
    val icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause

    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp)
            .widthIn(min = 56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.onSurface,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OliviaRecordButton(
    live: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        pressScale(pressed),
        OliviaMotion.press(),
        label = "record press",
    )
    val description = if (live) "Parar gravação" else OliviaDestination.RECORD.label
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = if (live) Role.Button else Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (live) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, OliviaRecordingRed, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .background(OliviaRecordingRed, CircleShape),
                )
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = OliviaBlueActionShadow,
                        spotColor = OliviaBlueActionShadow,
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(OliviaBlueActionStart, OliviaBlueActionEnd),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = OliviaBlueActionOn,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
