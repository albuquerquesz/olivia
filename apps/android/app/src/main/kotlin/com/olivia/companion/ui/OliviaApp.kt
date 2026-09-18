package com.olivia.companion.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.NavHost
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.olivia.companion.session.OliviaSessionViewModel
import com.olivia.companion.session.LocalSessionCommand
import com.olivia.companion.session.SessionRuntimeFactory
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.session.VoiceCommandState
import com.olivia.core.SessionId
import kotlinx.coroutines.launch

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

private fun openVoiceSettings(context: android.content.Context) {
    val voiceSettings = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    val settingsIntent = if (voiceSettings.resolveActivity(context.packageManager) != null) {
        voiceSettings
    } else {
        Intent(Settings.ACTION_SETTINGS)
    }
    context.startActivity(settingsIntent)
}

private fun bottomBarPadding(showBottomBar: Boolean) =
    80.dp.takeIf { showBottomBar } ?: 0.dp

private fun pressScale(pressed: Boolean): Float {
    if (pressed) return 0.94f
    return 1f
}

@Composable
private fun navigationColor(selected: Boolean) =
    MaterialTheme.colorScheme.primary.takeIf { selected }
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
    var audioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        audioPermissionGranted = granted
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = OliviaDestination.entries.any { it.route == currentRoute }
    val travelPx = with(LocalDensity.current) { OliviaMotion.travel.roundToPx() }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var permissionRequested by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.commandError) {
        uiState.commandError?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(uiState.voiceState) {
        if (uiState.voiceState == VoiceCommandState.UNAVAILABLE) {
            val result = snackbar.showSnackbar(
                message = "Comandos de voz desativados neste dispositivo.",
                actionLabel = "Configurar",
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                openVoiceSettings(context)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.voiceNavigation.collect { command ->
            val destination = if (command == LocalSessionCommand.STOP) {
                OliviaDestination.HOME.route
            } else {
                OliviaDestination.RECORD.route
            }
            val routeAtEvent = navController.currentDestination?.route
            navController.navigate(destination) {
                if (routeAtEvent == WELCOME_ROUTE) {
                    popUpTo(WELCOME_ROUTE) { inclusive = true }
                } else {
                    popUpTo(OliviaDestination.HOME.route) { saveState = true }
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(currentRoute, audioPermissionGranted) {
        if (
            currentRoute != null &&
            currentRoute != WELCOME_ROUTE &&
            !audioPermissionGranted && !permissionRequested &&
            uiState.voiceState != VoiceCommandState.UNAVAILABLE
        ) {
            permissionRequested = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Voice listening follows the activity lifecycle, not the navigation destination. Re-keying
    // this effect by currentRoute would stop/start the detector on every tab change; an unavailable
    // provider could then report an audible error even though the user never asked for voice input.
    DisposableEffect(lifecycleOwner, audioPermissionGranted) {
        if (!audioPermissionGranted) {
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

    Scaffold(snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) }) { innerPadding ->
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
                    onEnter = {
                        navController.navigate(OliviaDestination.HOME.route) {
                            popUpTo(WELCOME_ROUTE) { inclusive = true }
                        }
                    },
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
                        navController.navigate(OliviaDestination.RECORD.route)
                    },
                    onOpenTake = { sessionId ->
                        navController.navigate("take/${sessionId.value}")
                    },
                )
            }
            composable(OliviaDestination.RECORD.route) {
                OliviaRecordingScreen(
                    state = uiState,
                    onOpenSettings = {
                        navController.navigate(OliviaDestination.SETTINGS.route) {
                            popUpTo(OliviaDestination.HOME.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenVoiceSettings = { openVoiceSettings(context) },
                    onConnectGlasses = {
                        val activity = context as? Activity
                        val result = activity?.let(SessionRuntimeFactory::startGlassesRegistration)
                            ?: Result.failure(IllegalStateException("Activity indisponível"))
                        coroutineScope.launch {
                            snackbar.showSnackbar(
                                if (result.isSuccess) {
                                    "Verificando permissão e componente DAT no Meta AI."
                                } else {
                                    "Não foi possível abrir o fluxo de conexão do DAT."
                                },
                            )
                        }
                    },
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
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) viewModel.importTake(sessionId, uri.toString())
                }
                TakeRouteContent(
                    take = viewModel.take(sessionId),
                    takes = uiState.takes,
                    exportState = uiState.exportState,
                    onExport = { viewModel.exportTake(sessionId) },
                    canImport = uiState.session?.id == sessionId && uiState.session?.state == com.olivia.core.SessionState.STOPPED && !uiState.working,
                    working = uiState.working,
                    onImport = { picker.launch("video/*") },
                    onExportMarks = { viewModel.exportMarks(sessionId) },
                    onDiscard = { viewModel.discardMedia(sessionId) },
                    onOpenTake = { id -> navController.navigate("take/${id.value}") },
                    onNavigateBack = { navController.popBackStack() },
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
            VoiceListeningOverlay(
                state = uiState.voiceState,
                modifier = Modifier.matchParentSize().zIndex(2f),
            )
        }
    }
}

/**
 * Assistant-style, non-interactive feedback for the local command window.
 *
 * Porcupine's wake-word listening remains silent; this overlay is deliberately driven by Rhino's
 * command-window state. It therefore appears only after the wake word and never claims that a
 * command is being captured while permission is missing, the activity is paused, or the engine
 * has failed. The overlay has no pointer input and does not alter navigation.
 */
@Composable
private fun VoiceListeningOverlay(
    state: VoiceCommandState,
    modifier: Modifier = Modifier,
) {
    // Porcupine remains silently armed for the wake word. The visible Assistant-like affordance
    // starts only after the wake word opens Rhino's short command window.
    if (state != VoiceCommandState.LISTENING_FOR_COMMAND) return

    val transition = rememberInfiniteTransition(label = "voice-listening-edge")
    val alpha by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-listening-alpha",
    )
    val colors = listOf(
        Color(0xFF8B5CF6),
        MaterialTheme.colorScheme.primary,
        Color(0xFF22C55E),
        Color(0xFF8B5CF6),
    )
    val message = "Ouvindo comando"

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
                .graphicsLayer {
                    this.alpha = alpha
                }
                .border(
                    BorderStroke(3.dp, Brush.sweepGradient(colors)),
                    RoundedCornerShape(24.dp),
                )
                .semantics {
                    contentDescription = message
                },
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(50),
            tonalElevation = 3.dp,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TakeRouteContent(
    take: TakeSummary?,
    takes: List<TakeSummary>,
    exportState: com.olivia.companion.session.ExportState,
    onExport: () -> Unit,
    canImport: Boolean,
    working: Boolean,
    onImport: () -> Unit,
    onExportMarks: () -> Unit,
    onDiscard: () -> Unit,
    onOpenTake: (SessionId) -> Unit,
    onNavigateBack: () -> Unit,
) {
    if (take != null) {
        OliviaTakeScreen(
            take = take,
            onNavigateBack = onNavigateBack,
            exportState = exportState,
            onExport = onExport,
            canImport = canImport,
            working = working,
            onImport = onImport,
            onExportMarks = onExportMarks,
            onDiscard = onDiscard,
        )
        return
    }
    OliviaSessionsScreen(takes, onOpenTake)
}

@Composable
private fun OliviaBottomBar(
    currentRoute: String?,
    onDestinationSelected: (OliviaDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
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
                        onClick = { onDestinationSelected(destination) },
                    )
                }
            }
        }
        OliviaRecordButton(
            selected = currentRoute == OliviaDestination.RECORD.route,
            onClick = { onDestinationSelected(OliviaDestination.RECORD) },
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        )
    }
}

@Composable
private fun OliviaNavigationItem(
    destination: OliviaDestination,
    selected: Boolean,
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
        Icon(
            destination.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Text(
            destination.label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OliviaRecordButton(
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
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .semantics { contentDescription = OliviaDestination.RECORD.label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
