package com.olivia.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Codec explícito de metadados locais/exportação. Nunca serializa Frame ou tokens.
 * Transcrição é conteúdo privado: saída deste codec não pode ser escrita em logs.
 */
public object ContractJson {
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
    }

    public fun encodeEvent(event: TimelineEvent): String {
        ContractVersion.requireCurrent(event.contractVersion)
        return json.encodeToString(event)
    }

    public fun decodeEvent(value: String): TimelineEvent {
        requireVersion(value)
        return json.decodeFromString<TimelineEvent>(value)
    }

    public fun encodeReview(review: SessionReview): String {
        validateReview(review)
        return json.encodeToString(review)
    }

    public fun decodeReview(value: String): SessionReview {
        requireVersion(value)
        json.parseToJsonElement(value).jsonObject["timeline"]?.jsonArray?.forEach {
            requireVersion(it.jsonObject)
        }
        return json.decodeFromString<SessionReview>(value).also(::validateReview)
    }

    private fun requireVersion(value: String) {
        requireVersion(json.parseToJsonElement(value).jsonObject)
    }

    private fun requireVersion(value: JsonObject) {
        // O default Kotlin não deve esconder versão ausente em arquivo externo.
        val version = value["contractVersion"]
        requireNotNull(version) { "Versão de contrato ausente" }
        ContractVersion.requireCurrent(version.jsonPrimitive.content)
    }

    private fun validateReview(review: SessionReview) {
        ContractVersion.requireCurrent(review.contractVersion)
        require(review.recording == null || review.recording.sessionId == review.sessionId) {
            "Gravação de outra sessão"
        }
        review.timeline.forEach {
            ContractVersion.requireCurrent(it.contractVersion)
            require(it.sessionId == review.sessionId) { "Evento de outra sessão" }
        }
    }
}
