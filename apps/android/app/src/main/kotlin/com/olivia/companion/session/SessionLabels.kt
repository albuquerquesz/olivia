package com.olivia.companion.session

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.olivia.core.AudioMode
import com.olivia.core.CorrectionStatus
import com.olivia.core.DiagnosisKind
import com.olivia.core.GlassesConnection
import com.olivia.core.SessionState
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType

fun formatOffset(offsetMs: Long): String {
    val totalSeconds = (offsetMs / 1_000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private val DayMonthFormatter = DateTimeFormatter.ofPattern("dd/MM")

fun formatRelativeDay(
    epochMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (epochMs <= 0) return ""
    val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    if (day == today) return "Hoje"
    if (day == today.minusDays(1)) return "Ontem"
    return day.format(DayMonthFormatter)
}

fun sessionStateLabel(state: SessionState): String = when (state) {
    SessionState.IDLE -> "Parada"
    SessionState.STARTING -> "Iniciando"
    SessionState.STARTED -> "Gravando"
    SessionState.PAUSED -> "Pausada"
    SessionState.DEGRADED -> "Orientação interrompida"
    SessionState.STOPPING -> "Encerrando"
    SessionState.STOPPED -> "Encerrada"
    SessionState.PROCESSING -> "Processando"
    SessionState.READY -> "Pronta para revisar"
    SessionState.ERROR -> "Erro"
}

fun sessionStatusLabel(session: com.olivia.core.SessionSnapshot): String {
    if (session.state != SessionState.DEGRADED) return sessionStateLabel(session.state)
    return session.degradationCauses.joinToString(" · ") { cause ->
        when (cause) {
            com.olivia.core.DegradationCause.CAPTURE_UNAVAILABLE -> "Sem frame"
            com.olivia.core.DegradationCause.ANALYSIS_UNAVAILABLE -> "Análise indisponível"
            com.olivia.core.DegradationCause.AUDIO_UNAVAILABLE -> "Áudio indisponível"
        }
    }
}

fun guidanceStatusLabel(status: LiveGuidanceStatus): String = when (status) {
    LiveGuidanceStatus.IDLE -> "Inicie um take para orientar por áudio."
    LiveGuidanceStatus.PAUSED -> "Orientação pausada."
    LiveGuidanceStatus.LOCATING_TARGET -> "Procurando a área da peça…"
    LiveGuidanceStatus.TARGET_NOT_FOUND -> "Área da peça ainda não localizada."
    LiveGuidanceStatus.TARGET_FOUND -> "Área da peça localizada."
    LiveGuidanceStatus.TARGET_CENTERED -> "Enquadramento acompanhado, sem correção agora."
    LiveGuidanceStatus.GUIDANCE_PENDING -> "Preparando orientação por áudio…"
    LiveGuidanceStatus.GUIDANCE_SENT -> "Orientação enviada; aguardando a correção."
    LiveGuidanceStatus.FIREBASE_CONFIGURATION_UNAVAILABLE ->
        "Acesso ao Firebase/App Check não autorizado. Confira o token do APK debug, app e quota."
    LiveGuidanceStatus.ANALYSIS_UNAVAILABLE -> "A análise está indisponível."
    LiveGuidanceStatus.AUDIO_UNAVAILABLE -> "Não foi possível reproduzir a orientação."
}

fun connectionLabel(connection: GlassesConnection): String = when (connection) {
    GlassesConnection.CONNECTED -> "Óculos conectados"
    GlassesConnection.DISCONNECTED -> "Sem frame"
    GlassesConnection.UNAVAILABLE -> "Óculos indisponíveis"
}

fun audioModeLabel(mode: AudioMode): String = when (mode) {
    AudioMode.VOICE -> "Modo Voz"
    AudioMode.SIGNAL -> "Modo Sinal"
}

fun eventTitle(event: TimelineEvent): String {
    if (event.type == TimelineEventType.CORRECTION_RESOLVED) return when (event.correctionStatus) {
        CorrectionStatus.OBEYED -> "Correção confirmada"
        CorrectionStatus.IGNORED -> "Correção não confirmada"
        CorrectionStatus.EXPIRED -> "Correção expirada"
        CorrectionStatus.SUPERSEDED -> "Correção substituída"
        else -> "Correção pendente"
    }
    if (event.type == TimelineEventType.CORRECTION_ISSUED) event.diagnosis?.let { diagnosis ->
        if (diagnosis.direction != null) return com.olivia.core.GuidanceCatalogue.phrase(diagnosis, AudioMode.VOICE)
    }
    if (event.type == TimelineEventType.DIAGNOSIS) return diagnosisKindLabel(event.diagnosis?.kind)
    event.label?.takeIf { it.isNotBlank() }?.let { return it }
    return when (event.type) {
        TimelineEventType.SESSION_STARTED -> "Sessão iniciada"
        TimelineEventType.SESSION_PAUSED -> "Pausa"
        TimelineEventType.SESSION_RESUMED -> "Retomada"
        TimelineEventType.SESSION_DEGRADED -> "Queda de conexão"
        TimelineEventType.SESSION_RECOVERED -> "Conexão restaurada"
        TimelineEventType.SESSION_STOPPED -> "Sessão encerrada"
        TimelineEventType.DIAGNOSIS -> diagnosisKindLabel(event.diagnosis?.kind)
        TimelineEventType.CORRECTION_ISSUED -> "Correção pedida"
        TimelineEventType.CORRECTION_RESOLVED -> "Correção conferida"
        TimelineEventType.CUT_SUGGESTED -> "Corte sugerido"
        TimelineEventType.ERROR -> "Erro"
    }
}

fun diagnosisKindLabel(kind: DiagnosisKind?): String = when (kind) {
    DiagnosisKind.FRAMING -> "Enquadramento"
    DiagnosisKind.LIGHT -> "Luz e sombra"
    DiagnosisKind.HORIZON -> "Horizonte"
    null -> "Diagnóstico"
}

fun confirmationLabel(event: TimelineEvent): String? {
    val status = event.correctionStatus ?: return confirmationLabelForType(event.type)
    return when (status) {
        CorrectionStatus.OBEYED -> "Confirmado"
        CorrectionStatus.PENDING -> "Não confirmado"
        CorrectionStatus.IGNORED -> "Não confirmado"
        CorrectionStatus.EXPIRED -> "Não confirmado"
        CorrectionStatus.SUPERSEDED -> "Não confirmado"
    }
}

private fun confirmationLabelForType(type: TimelineEventType): String? {
    if (type == TimelineEventType.CUT_SUGGESTED) return "Sugestão"
    if (
        type == TimelineEventType.DIAGNOSIS ||
        type == TimelineEventType.CORRECTION_ISSUED ||
        type == TimelineEventType.CORRECTION_RESOLVED
    ) {
        return "Não confirmado"
    }
    return null
}

fun isConfirmed(event: TimelineEvent): Boolean =
    event.correctionStatus == CorrectionStatus.OBEYED
