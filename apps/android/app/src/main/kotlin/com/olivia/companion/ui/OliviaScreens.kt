package com.olivia.companion.ui

import androidx.compose.runtime.Composable
import com.olivia.companion.session.ExportState
import com.olivia.companion.session.OliviaUiState
import com.olivia.companion.session.TakeSummary
import com.olivia.companion.ui.screens.OliviaHomeScreen as HomeScreen
import com.olivia.companion.ui.screens.OliviaRecordingScreen as RecordingScreen
import com.olivia.companion.ui.screens.OliviaSessionsScreen as SessionsScreen
import com.olivia.companion.ui.screens.OliviaSettingsScreen as SettingsScreen
import com.olivia.companion.ui.screens.OliviaTakeScreen as TakeScreen
import com.olivia.companion.ui.screens.OliviaWelcomeScreen as WelcomeScreen
import com.olivia.core.AudioMode
import com.olivia.core.RecordingAsset
import com.olivia.core.SessionId

@Composable
fun OliviaWelcomeScreen(
    audioPermissionGranted: Boolean,
    bluetoothPermissionRequired: Boolean,
    bluetoothPermissionGranted: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onEnter: () -> Unit,
) {
    WelcomeScreen(
        audioPermissionGranted = audioPermissionGranted,
        bluetoothPermissionRequired = bluetoothPermissionRequired,
        bluetoothPermissionGranted = bluetoothPermissionGranted,
        onRequestAudioPermission = onRequestAudioPermission,
        onRequestBluetoothPermission = onRequestBluetoothPermission,
        onEnter = onEnter,
    )
}

@Composable
fun OliviaHomeScreen(
    state: OliviaUiState,
    onSeeAllTakes: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTake: (SessionId) -> Unit,
) {
    HomeScreen(
        state = state,
        onSeeAllTakes = onSeeAllTakes,
        onOpenRecord = onOpenRecord,
        onOpenSettings = onOpenSettings,
        onOpenTake = onOpenTake,
    )
}

@Composable
fun OliviaRecordingScreen(
    state: OliviaUiState,
    onNavigateBack: () -> Unit,
    onOpenVoiceSettings: () -> Unit = {},
    onConnectGlasses: () -> Unit = {},
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDegrade: () -> Unit,
    onRecover: () -> Unit,
    onStop: () -> Unit,
) {
    RecordingScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onOpenVoiceSettings = onOpenVoiceSettings,
        onConnectGlasses = onConnectGlasses,
        onStart = onStart,
        onPause = onPause,
        onResume = onResume,
        onDegrade = onDegrade,
        onRecover = onRecover,
        onStop = onStop,
    )
}

@Composable
fun OliviaSessionsScreen(takes: List<TakeSummary>, onOpenTake: (SessionId) -> Unit) {
    SessionsScreen(takes = takes, onOpenTake = onOpenTake)
}

@Composable
fun OliviaTakeScreen(
    take: TakeSummary,
    onNavigateBack: () -> Unit,
    exportState: ExportState = ExportState(),
    onExport: () -> Unit = {},
    recordingPathResolver: suspend (RecordingAsset) -> String? = { null },
) {
    TakeScreen(
        take = take,
        onNavigateBack = onNavigateBack,
        exportState = exportState,
        onExport = onExport,
        recordingPathResolver = recordingPathResolver,
    )
}

@Composable
fun OliviaSettingsScreen(
    audioMode: AudioMode,
    live: Boolean,
    onAudioMode: (AudioMode) -> Unit,
) {
    SettingsScreen(
        audioMode = audioMode,
        live = live,
        onAudioMode = onAudioMode,
    )
}
