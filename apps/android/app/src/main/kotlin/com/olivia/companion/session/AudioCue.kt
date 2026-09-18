package com.olivia.companion.session

import com.olivia.companion.R
import com.olivia.core.Direction
import com.olivia.core.SessionSignal

/** Intenções sonoras traduzidas para arquivos WAV locais. */
internal enum class AudioCue(val resourceId: Int) {
    DIRECTION_LEFT(R.raw.cue_direction_left),
    DIRECTION_RIGHT(R.raw.cue_direction_right),
    DIRECTION_NEUTRAL(R.raw.cue_direction_neutral),
    SESSION_STARTED(R.raw.cue_session_started),
    SESSION_PAUSED(R.raw.cue_session_paused),
    SESSION_RESUMED(R.raw.cue_session_resumed),
    SESSION_STOPPED(R.raw.cue_session_stopped),
    CONNECTION_LOST(R.raw.cue_connection_lost),
    CONNECTION_RESTORED(R.raw.cue_connection_restored),
    VOICE_COMMAND_ERROR(R.raw.cue_voice_command_error),
    VOICE_INPUT_UNAVAILABLE(R.raw.cue_voice_input_unavailable),
}

internal fun cueFor(direction: Direction): AudioCue = when (direction) {
    Direction.TURN_LEFT,
    Direction.LEVEL_HORIZON_LEFT -> AudioCue.DIRECTION_LEFT
    Direction.TURN_RIGHT,
    Direction.LEVEL_HORIZON_RIGHT -> AudioCue.DIRECTION_RIGHT
    Direction.MOVE_CLOSER,
    Direction.MOVE_BACK,
    Direction.RAISE,
    Direction.LOWER,
    Direction.FACE_THE_LIGHT,
    Direction.AVOID_BACKLIGHT,
    Direction.LEAVE_THE_SHADOW -> AudioCue.DIRECTION_NEUTRAL
}

internal fun cueFor(signal: SessionSignal): AudioCue = when (signal) {
    SessionSignal.SESSION_STARTED -> AudioCue.SESSION_STARTED
    SessionSignal.SESSION_PAUSED -> AudioCue.SESSION_PAUSED
    SessionSignal.SESSION_RESUMED -> AudioCue.SESSION_RESUMED
    SessionSignal.SESSION_STOPPED -> AudioCue.SESSION_STOPPED
    SessionSignal.CONNECTION_LOST -> AudioCue.CONNECTION_LOST
    SessionSignal.CONNECTION_RESTORED -> AudioCue.CONNECTION_RESTORED
    SessionSignal.ANALYSIS_UNAVAILABLE, SessionSignal.AUDIO_UNAVAILABLE -> AudioCue.CONNECTION_LOST
    SessionSignal.ANALYSIS_RESTORED, SessionSignal.AUDIO_RESTORED -> AudioCue.CONNECTION_RESTORED
    SessionSignal.VOICE_COMMAND_ERROR -> AudioCue.VOICE_COMMAND_ERROR
    SessionSignal.VOICE_INPUT_UNAVAILABLE -> AudioCue.VOICE_INPUT_UNAVAILABLE
    SessionSignal.TARGET_FRAMED -> AudioCue.CONNECTION_RESTORED
}

internal fun spokenTextFor(direction: Direction, magnitude: Double?): String {
    return com.olivia.core.GuidanceCatalogue.phrase(
        com.olivia.core.VisualDiagnosis(direction.kind, com.olivia.core.Severity.MAJOR,
            com.olivia.core.Confidence(1f), direction),
        com.olivia.core.AudioMode.VOICE,
    )
}

internal fun spokenTextFor(signal: SessionSignal): String = when (signal) {
    SessionSignal.SESSION_STARTED -> "Sessão iniciada"
    SessionSignal.SESSION_PAUSED -> "Sessão pausada"
    SessionSignal.SESSION_RESUMED -> "Sessão retomada"
    SessionSignal.SESSION_STOPPED -> "Sessão encerrada"
    SessionSignal.CONNECTION_LOST -> "Conexão perdida"
    SessionSignal.CONNECTION_RESTORED -> "Conexão restaurada"
    SessionSignal.ANALYSIS_UNAVAILABLE -> "Análise indisponível"
    SessionSignal.ANALYSIS_RESTORED -> "Análise retomada"
    SessionSignal.AUDIO_UNAVAILABLE -> "Áudio indisponível"
    SessionSignal.AUDIO_RESTORED -> "Áudio retomado"
    SessionSignal.VOICE_COMMAND_ERROR -> "Não entendi o comando"
    SessionSignal.VOICE_INPUT_UNAVAILABLE -> "Comandos de voz indisponíveis"
    SessionSignal.TARGET_FRAMED -> "Enquadramento correto"
}
