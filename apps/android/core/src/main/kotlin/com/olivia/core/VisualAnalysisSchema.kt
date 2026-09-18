package com.olivia.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Versioned, closed response format expected from the visual locator. */
public object WorkAreaLocalizationSchema {
    public const val VERSION: String = "2.0"

    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    /**
     * Parses untrusted model output into a normalized work-area location. This never accepts prose,
     * extra fields, a direction, a diagnosis, a magnitude or an unbounded rectangle.
     * The caller decides how a controlled failure changes session health; raw output must not be
     * logged or persisted.
     */
    public fun decode(value: String): Result<WorkAreaLocalization> = runCatching {
        val root = json.parseToJsonElement(value).jsonObject
        root.requireExactKeys(setOf("schemaVersion", "targetPresent", "confidence", "roi"))
        require(root.requiredString("schemaVersion") == VERSION) { "Unexpected schema version" }
        val targetPresent = root.required("targetPresent").jsonPrimitive.contentOrNull
            ?.toBooleanStrictOrNull() ?: throw IllegalArgumentException("Invalid targetPresent")
        val confidence = Confidence(root.required("confidence").jsonPrimitive.floatOrNull
            ?: throw IllegalArgumentException("Invalid confidence"))
        val roi = root.required("roi").let { value ->
            if (value is JsonNull) null else {
                val item = value.jsonObject
                item.requireExactKeys(setOf("x", "y", "width", "height"))
                NormalizedRoi(
                    x = item.requiredFloat("x"),
                    y = item.requiredFloat("y"),
                    width = item.requiredFloat("width"),
                    height = item.requiredFloat("height"),
                )
            }
        }
        WorkAreaLocalization(targetPresent, confidence, roi)
    }

    private fun JsonObject.requireExactKeys(expected: Set<String>) {
        require(keys == expected) { "Invalid schema fields" }
    }

    private fun JsonObject.required(name: String): kotlinx.serialization.json.JsonElement =
        requireNotNull(this[name]) { "Missing required field: $name" }

    private fun JsonObject.requiredString(name: String): String =
        required(name).jsonPrimitive.contentOrNull ?: throw IllegalArgumentException("Invalid text: $name")

    private fun JsonObject.requiredFloat(name: String): Float = required(name).jsonPrimitive.floatOrNull
        ?: throw IllegalArgumentException("Invalid number: $name")
}
