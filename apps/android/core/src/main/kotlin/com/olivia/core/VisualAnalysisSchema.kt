package com.olivia.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Versioned, closed response format expected from the visual model. */
public object VisualAnalysisSchema {
    public const val VERSION: String = "1.0"

    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    /**
     * Parses untrusted model output into the closed domain vocabulary. This never accepts prose,
     * extra fields, duplicate categories, incompatible directions or an uncalibrated magnitude.
     * The caller decides how a controlled failure changes session health; raw output must not be
     * logged or persisted.
     */
    public fun decode(value: String): Result<List<VisualDiagnosis>> = runCatching {
        val root = json.parseToJsonElement(value).jsonObject
        root.requireExactKeys(setOf("schemaVersion", "diagnoses"))
        require(root.requiredString("schemaVersion") == VERSION) { "Unexpected schema version" }
        val diagnoses = root.required("diagnoses").jsonArray
        require(diagnoses.size <= DiagnosisKind.entries.size) { "Too many diagnoses" }
        diagnoses.map(::decodeDiagnosis).also { parsed ->
            require(parsed.map(VisualDiagnosis::kind).distinct().size == parsed.size) {
                "Duplicate diagnoses"
            }
        }
    }

    private fun decodeDiagnosis(value: JsonElement): VisualDiagnosis {
        val item = value.jsonObject
        item.requireExactKeys(setOf("kind", "severity", "confidence", "direction", "magnitude"))
        val kind = enumValueOf<DiagnosisKind>(item.requiredString("kind"))
        val severity = enumValueOf<Severity>(item.requiredString("severity"))
        val confidence = Confidence(item.required("confidence").jsonPrimitive.floatOrNull
            ?: throw IllegalArgumentException("Invalid confidence"))
        val direction = item.nullableString("direction")?.let { enumValueOf<Direction>(it) }
        val magnitude = item.nullableFloat("magnitude")?.toDouble()
        // The MVP has no glasses calibration, so numeric actions remain invalid model output.
        require(magnitude == null) { "Magnitude requires verified calibration" }
        return VisualDiagnosis(kind, severity, confidence, direction, magnitude)
    }

    private fun JsonObject.requireExactKeys(expected: Set<String>) {
        require(keys == expected) { "Invalid schema fields" }
    }

    private fun JsonObject.required(name: String): JsonElement =
        requireNotNull(this[name]) { "Missing required field: $name" }

    private fun JsonObject.requiredString(name: String): String =
        required(name).jsonPrimitive.contentOrNull ?: throw IllegalArgumentException("Invalid text: $name")

    private fun JsonObject.nullableString(name: String): String? = nullablePrimitive(name)?.contentOrNull

    private fun JsonObject.nullableFloat(name: String): Float? = nullablePrimitive(name)?.floatOrNull

    private fun JsonObject.nullablePrimitive(name: String): JsonPrimitive? = when (val value = required(name)) {
        JsonNull -> null
        is JsonPrimitive -> value
        else -> throw IllegalArgumentException("Invalid value: $name")
    }
}
