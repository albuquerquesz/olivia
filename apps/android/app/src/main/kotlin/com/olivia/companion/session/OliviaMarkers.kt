package com.olivia.companion.session

import com.olivia.core.SessionReview
import com.olivia.core.TimelineEvent
import com.olivia.core.TimelineEventType
import java.util.Locale

/**
 * Serializa somente marcas derivadas da timeline. Frames, áudio bruto e credenciais não
 * entram no artefato, que é seguro para o handoff manual ao editor usado pela pessoa.
 */
object OliviaMarkersJson {
    const val FORMAT_VERSION = 1

    fun encode(review: SessionReview): String {
        val durationMs = review.recording?.durationMs?.takeIf { it > 0 }
            ?: review.timeline.maxOfOrNull { it.offsetMs }.orZero()
        val markers = review.timeline
            .filter { it.type != TimelineEventType.SESSION_STARTED || it.offsetMs == 0L }
            .sortedBy { it.offsetMs }
            .joinToString(",\n") { markerJson(it) }
        val cuts = review.suggestedCuts.joinToString(",\n") { cut ->
            "    {\n" +
                "      \"startMs\": ${cut.startMs},\n" +
                "      \"endMs\": ${cut.endMs},\n" +
                "      \"reason\": ${quote(cut.reason.name)},\n" +
                "      \"confidence\": ${formatNumber(cut.confidence.value)}\n" +
                "    }"
        }
        return """
{
  "version": $FORMAT_VERSION,
  "contractVersion": ${quote(review.contractVersion)},
  "sessionId": ${quote(review.sessionId.value)},
  "durationMs": $durationMs,
  "markers": [
$markers
  ],
  "suggestedCuts": [
$cuts
  ]
}
""".trimIndent() + "\n"
    }

    private fun markerJson(event: TimelineEvent): String {
        val fields = listOf(
            "      \"offsetMs\": ${event.offsetMs}",
            "      \"type\": ${quote(event.type.name)}",
            "      \"label\": ${event.label?.let(::quote) ?: "null"}",
            "      \"direction\": ${event.direction?.name?.let(::quote) ?: "null"}",
            "      \"status\": ${event.correctionStatus?.name?.let(::quote) ?: "null"}",
        )
        return "    {\n${fields.joinToString(",\n")}\n    }"
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun formatNumber(value: Float): String =
        String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')

    private fun Long?.orZero(): Long = this ?: 0L
}
