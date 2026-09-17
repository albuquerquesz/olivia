package com.olivia.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.olivia.companion.GlassesBackend
import com.olivia.companion.media.AndroidLocalRecordingSource
import com.olivia.companion.media.AndroidReviewExporter
import com.olivia.companion.media.LocalReviewExport
import com.olivia.companion.session.LocalSessionCommand
import com.olivia.companion.session.LiveGuidanceStatus
import com.olivia.companion.session.SessionRuntime
import com.olivia.companion.session.SessionHistoryEntry
import com.olivia.core.AudioMode
import com.olivia.core.DegradationCause
import com.olivia.core.LinearMediaAlignment
import com.olivia.core.LocalPostProcessor
import com.olivia.core.SessionSnapshot
import com.olivia.core.SessionState
import com.olivia.core.UnavailableLocalTranscriptProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun OliviaWelcomeScreen(onContinue: () -> Unit) {
    OliviaPage {
        OliviaPanel {
            Text("Olivia", style = MaterialTheme.typography.bodyLarge)
            Text("Grava com as mãos livres", style = MaterialTheme.typography.headlineMedium)
            PanelDetail(
                "Eu oriento enquadramento, luz e horizonte por áudio enquanto você trabalha.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Comando de voz", style = MaterialTheme.typography.bodyLarge)
                Text("Oi, Olivia, Gravar", style = MaterialTheme.typography.headlineSmall)
            }
            PanelDetail("Frames de análise são efêmeros. Sem conta.")
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Continuar")
        }
    }
}

@Composable
fun OliviaHomeScreen(sessionRuntime: SessionRuntime) {
    val history by sessionRuntime.sessionHistory.collectAsState()
    OliviaPage {
        OliviaPanel {
            Text("Dispositivo", style = MaterialTheme.typography.bodyLarge)
            Text("Ray-Ban Meta", style = MaterialTheme.typography.headlineMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionTitle("Seu resumo")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                OliviaStat("Sessões", history.size.toString(), Modifier.weight(1f))
                OliviaStat(
                    "Correções",
                    history.sumOf(SessionHistoryEntry::correctionCount).toString(),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun OliviaRecordingScreen(
    backend: GlassesBackend,
    sessionRuntime: SessionRuntime,
    onOpenSettings: () -> Unit,
) {
    val snapshot by sessionRuntime.snapshot.collectAsState()
    val guidanceStatus by sessionRuntime.liveGuidanceStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var commandError by rememberSaveable { mutableStateOf<String?>(null) }
    OliviaPage {
        OliviaPanel {
            Text("Comando de voz", style = MaterialTheme.typography.bodyLarge)
            Text("Oi, Olivia, Gravar", style = MaterialTheme.typography.headlineMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceStatus(backend)
                SessionStatus(snapshot)
                LiveGuidanceStatusPanel(guidanceStatus, snapshot?.state)
                PanelDetail(
                    if (backend.requiresPhysicalGlasses) {
                        "É necessário conectar os óculos para gravar."
                    } else {
                        "Prévia local com óculos simulados. O wake word Porcupine ainda não está configurado."
                    },
                )
            }
        }
        Text(
            "Os comandos usam o microfone do celular. A orientação chega por áudio.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!backend.requiresPhysicalGlasses) {
            MockSessionControls(
                state = snapshot?.state,
                onCommand = { command ->
                    commandError = null
                    scope.launch {
                        sessionRuntime.handle(command).onFailure {
                            commandError = "Não foi possível executar a demonstração"
                        }
                    }
                },
            )
            commandError?.let { PanelDetail(it) }
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("Configurar áudio e dispositivo", modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun SessionStatus(snapshot: SessionSnapshot?) {
    val state = snapshot?.state
    val degradationCauses = snapshot?.degradationCauses.orEmpty()
    val label = when (state) {
        SessionState.STARTING -> "Preparando captura"
        SessionState.STARTED -> "Gravacao em andamento"
        SessionState.PAUSED -> "Gravacao pausada"
        SessionState.DEGRADED -> when {
            DegradationCause.ANALYSIS_UNAVAILABLE in degradationCauses ->
                "Analise indisponivel"
            DegradationCause.AUDIO_UNAVAILABLE in degradationCauses ->
                "Audio indisponivel"
            else -> "Conexao indisponivel"
        }
        SessionState.STOPPING -> "Encerrando"
        SessionState.STOPPED -> "Take encerrado"
        SessionState.PROCESSING -> "Preparando revisao"
        SessionState.READY -> "Revisao pronta"
        SessionState.ERROR -> "Sessao indisponivel"
        SessionState.IDLE, null -> "Aguardando comando de voz"
    }
    Text(label, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun LiveGuidanceStatusPanel(status: LiveGuidanceStatus, state: SessionState?) {
    val message = when (status) {
        LiveGuidanceStatus.IDLE -> if (state == SessionState.STOPPED) {
            "Take encerrado. Consulte Sessões para ver o resumo local."
        } else {
            "Inicie um take para procurar a área da peça."
        }
        LiveGuidanceStatus.PAUSED ->
            "Take pausado. Retome quando quiser continuar a análise."
        LiveGuidanceStatus.LOCATING_TARGET ->
            "Procurando a área da peça no primeiro frame…"
        LiveGuidanceStatus.TARGET_NOT_FOUND ->
            "Área não identificada. Aponte os óculos para a peça e aguarde."
        LiveGuidanceStatus.TARGET_FOUND ->
            "Área encontrada. Avaliando enquadramento, luz e horizonte."
        LiveGuidanceStatus.TARGET_CENTERED ->
            "Área encontrada; sem correção neste momento."
        LiveGuidanceStatus.GUIDANCE_PENDING ->
            "Correção encontrada; preparando orientação por áudio."
        LiveGuidanceStatus.GUIDANCE_SENT ->
            "Orientação enviada por áudio."
        LiveGuidanceStatus.ANALYSIS_UNAVAILABLE ->
            "Análise indisponível; tentando um frame mais recente."
        LiveGuidanceStatus.AUDIO_UNAVAILABLE ->
            "Análise concluída, mas o áudio não pôde iniciar."
    }
    PanelDetail(message)
}

@Composable
fun OliviaSessionsScreen(sessionRuntime: SessionRuntime) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember { AndroidLocalRecordingSource(context.applicationContext) }
    val exporter = remember { AndroidReviewExporter(context.applicationContext) }
    val snapshot by sessionRuntime.snapshot.collectAsState()
    val history by sessionRuntime.sessionHistory.collectAsState()
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var importedVideo by remember { mutableStateOf<File?>(null) }
    var importedAssetId by remember { mutableStateOf<String?>(null) }
    var reviewExport by remember { mutableStateOf<LocalReviewExport?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val stoppedSession = snapshot?.takeIf { it.state == SessionState.STOPPED }
        if (uri == null) {
            importMessage = "Importação cancelada"
        } else if (stoppedSession == null) {
            importMessage = "Encerre o take antes de importar o vídeo"
        } else {
            scope.launch {
                importMessage = "Validando vídeo local…"
                try {
                    val asset = importer.registerSelectedTake(stoppedSession, uri).getOrElse {
                        importMessage = "Não foi possível usar este vídeo"
                        return@launch
                    }
                    val previousAssetId = importedAssetId
                    val previousReview = reviewExport
                    importer.fetch(asset).collect { progress ->
                        importMessage = "Importando vídeo: ${progress.transferredBytes / 1024} KB"
                    }
                    previousAssetId?.let { importer.discardImportedCopy(it) }
                    previousReview?.let { exporter.discard(it) }
                    importedVideo = importer.localPathOf(asset.id)?.let(::File)
                    importedAssetId = asset.id
                    importMessage = "Preparando revisão local…"
                    val review = sessionRuntime.processPostTake { sessionId ->
                        val timeline = sessionRuntime.timelineFor(sessionId).getOrDefault(emptyList())
                        LocalPostProcessor(
                            transcriptProvider = UnavailableLocalTranscriptProvider,
                            alignmentFor = { recording ->
                                LinearMediaAlignment(sessionId, recording.startedAtEpochMs, 0)
                            },
                        ).process(sessionId, asset, timeline)
                    }.getOrElse {
                        importMessage = "Não foi possível preparar a revisão"
                        return@launch
                    }
                    reviewExport = exporter.persist(review).getOrNull()
                    importMessage = if (review.transcript.isEmpty()) {
                        "Vídeo e marcas locais prontos. A transcrição aguarda o modelo Whisper."
                    } else {
                        "Revisão local pronta."
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    importMessage = "A importação não foi concluída"
                }
            }
        }
    }
    OliviaPage {
        OliviaPanel {
            Text("Seus takes", style = MaterialTheme.typography.bodyLarge)
            Text(
                when (snapshot?.state) {
                    SessionState.STOPPED -> "Take encerrado"
                    SessionState.PROCESSING -> "Preparando revisão"
                    SessionState.READY -> "Revisão pronta"
                    else -> if (history.isEmpty()) "Nenhuma sessão encerrada" else "Histórico local"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            PanelDetail(
                if (snapshot?.state == SessionState.STOPPED) {
                    "A sessão de orientação foi registrada. O loop ao vivo não grava vídeo; selecione o arquivo final para revisar localmente."
                } else if (history.isNotEmpty()) {
                    "Este resumo guarda somente metadados locais. Frames e áudio de análise foram descartados."
                } else {
                    "Depois de encerrar um take, o resumo e as marcas aparecerão aqui."
                },
            )
        }
        history.takeLast(5).asReversed().forEach { entry ->
            SessionHistoryCard(entry)
        }
        OutlinedButton(
            onClick = { picker.launch("video/*") },
            enabled = snapshot?.state == SessionState.STOPPED,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("Selecionar vídeo para revisão")
        }
        importMessage?.let { message ->
            PanelDetail(message)
        }
        importedVideo?.let { video ->
            OutlinedButton(
                onClick = {
                    importMessage = exporter.shareVideo(video).fold(
                        onSuccess = { "Abrindo opções de edição…" },
                        onFailure = { "Não foi possível compartilhar o vídeo local" },
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Enviar vídeo para editar")
            }
        }
        reviewExport?.let { export ->
            OutlinedButton(
                onClick = {
                    importMessage = exporter.shareReview(export).fold(
                        onSuccess = { "Abrindo compartilhamento das marcas…" },
                        onFailure = { "Não foi possível compartilhar as marcas locais" },
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Compartilhar marcas do take")
            }
        }
        if (importedAssetId != null || reviewExport != null) {
            TextButton(
                onClick = {
                    scope.launch {
                        importedAssetId?.let { importer.discardImportedCopy(it) }
                        reviewExport?.let { exporter.discard(it) }
                        importedAssetId = null
                        importedVideo = null
                        reviewExport = null
                        importMessage = "Cópias locais temporárias descartadas"
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Descartar cópias locais")
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(entry: SessionHistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sessão ${entry.sessionId.take(8)}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${entry.diagnosisCount} análises · ${entry.correctionCount} orientações",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Resumo local salvo; nenhum frame foi armazenado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Development-only input for the deterministic mock; this never represents voice recognition. */
@Composable
private fun MockSessionControls(
    state: SessionState?,
    onCommand: (LocalSessionCommand) -> Unit,
) {
    when (state) {
        SessionState.STARTING, SessionState.STARTED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onCommand(LocalSessionCommand.PAUSE) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("Pausar demonstração") }
                Button(
                    onClick = { onCommand(LocalSessionCommand.STOP) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("Terminar") }
            }
        }
        SessionState.PAUSED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onCommand(LocalSessionCommand.RESUME) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("Retomar") }
                Button(
                    onClick = { onCommand(LocalSessionCommand.STOP) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("Terminar") }
            }
        }
        SessionState.DEGRADED -> Button(
            onClick = { onCommand(LocalSessionCommand.STOP) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Terminar demonstração") }
        SessionState.STOPPING, SessionState.PROCESSING -> PanelDetail("Aguarde a mudança de estado")
        SessionState.READY -> PanelDetail("Abra Sessões para revisar e exportar")
        SessionState.ERROR -> Button(
            onClick = { onCommand(LocalSessionCommand.START) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Reiniciar demonstração") }
        SessionState.IDLE, SessionState.STOPPED, null -> Button(
            onClick = { onCommand(LocalSessionCommand.START) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
        ) { Text("Iniciar demonstração") }
    }
}

@Composable
fun OliviaSettingsScreen(
    audioMode: AudioMode,
    onAudioModeSelected: suspend (AudioMode) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    OliviaPage(onNavigateBack = onNavigateBack) {
        OliviaPanel {
            SectionTitle("Modo de feedback")
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AudioModeOption("Voz", audioMode == AudioMode.VOICE) {
                    scope.launch { onAudioModeSelected(AudioMode.VOICE) }
                }
                AudioModeOption("Sinal", audioMode == AudioMode.SIGNAL) {
                    scope.launch { onAudioModeSelected(AudioMode.SIGNAL) }
                }
            }
            PanelDetail(
                if (audioMode == AudioMode.VOICE) {
                    "Instruções curtas, faladas pela Olivia."
                } else {
                    "Tons locais de 80 ms para orientar a correção."
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Meu perfil")
            Text("Mariana · Tatuadora", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Perfil local do protótipo, sem conta ou rede social.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Privacidade")
            Text(
                "Frames de análise são efêmeros. O áudio só é transcrito depois que a sessão termina.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OliviaPage(
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar")
                }
            }
            content()
        }
    }
}

@Composable
private fun OliviaPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.heightIn(min = 248.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}

@Composable
private fun DeviceStatus(backend: GlassesBackend) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface),
    ) {
        Text(
            if (backend.requiresPhysicalGlasses) "Óculos físicos necessários" else "Modo demonstração",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PanelDetail(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.inverseOnSurface,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun OliviaStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) colors.onPrimary else colors.primary,
        OliviaMotion.selection(),
        label = "audio option background",
    )
    val foreground by animateColorAsState(
        if (selected) colors.primary else colors.onPrimary,
        OliviaMotion.selection(),
        label = "audio option foreground",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        OliviaMotion.press(),
        label = "audio option press",
    )
    Surface(
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, colors.inverseOnSurface),
    ) {
        Row(
            modifier = Modifier
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .semantics(mergeDescendants = true) {}
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            } else {
                Spacer(Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
