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
    return when (day) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> day.format(DayMonthFormatter)
    }
}

fun sessionStateLabel(state: SessionState): String = when (state) {
    SessionState.IDLE -> "Parada"
    SessionState.STARTING -> "Iniciando"
    SessionState.STARTED -> "Gravando"
    SessionState.PAUSED -> "Pausada"
    SessionState.DEGRADED -> "Conexão perdida"
    SessionState.STOPPING -> "Encerrando"
    SessionState.STOPPED -> "Encerrada"
    SessionState.PROCESSING -> "Processando"
    SessionState.READY -> "Pronta para revisar"
    SessionState.ERROR -> "Erro"
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
    val status = event.correctionStatus ?: return when (event.type) {
        TimelineEventType.CUT_SUGGESTED -> "Sugestão"
        TimelineEventType.DIAGNOSIS,
        TimelineEventType.CORRECTION_ISSUED,
        TimelineEventType.CORRECTION_RESOLVED,
        -> "Não confirmado"
        else -> null
    }
    return when (status) {
        CorrectionStatus.OBEYED -> "Confirmado"
        CorrectionStatus.PENDING -> "Não confirmado"
        CorrectionStatus.IGNORED -> "Não confirmado"
        CorrectionStatus.EXPIRED -> "Não confirmado"
        CorrectionStatus.SUPERSEDED -> "Não confirmado"
    }
}

fun isConfirmed(event: TimelineEvent): Boolean =
    event.correctionStatus == CorrectionStatus.OBEYED
