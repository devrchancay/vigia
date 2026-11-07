package com.ramonchancay.vigia.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface ConfigParseResult {
    data class Valid(val config: PanelConfig) : ConfigParseResult

    data class Invalid(val errors: List<ConfigError>) : ConfigParseResult
}

/** Turns a JSON document into a validated [PanelConfig]. Never throws. */
object PanelConfigParser {
    /**
     * Unknown keys are rejected so a typo ("colums") is reported instead of silently ignored.
     * Defaults are not written back when encoding, which keeps stored configs short.
     */
    val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = false
        prettyPrint = false
    }

    // kotlinx.serialization reports the failing location as "at path $.widgets[0].pos" (JSON path).
    private val jsonPath = Regex("""at path:? \$\.?(\S*)""")

    fun parse(text: String): ConfigParseResult {
        val config = try {
            json.decodeFromString(PanelConfig.serializer(), text)
        } catch (e: SerializationException) {
            return ConfigParseResult.Invalid(listOf(malformed(e)))
        } catch (e: IllegalArgumentException) {
            return ConfigParseResult.Invalid(listOf(malformed(e)))
        }
        val errors = PanelConfigValidator.validate(config)
        return if (errors.isEmpty()) ConfigParseResult.Valid(config) else ConfigParseResult.Invalid(errors)
    }

    fun encode(config: PanelConfig): String = json.encodeToString(PanelConfig.serializer(), config)

    private fun malformed(e: Exception): ConfigError {
        val message = e.message.orEmpty().lineSequence().firstOrNull().orEmpty()
        val path = jsonPath.find(e.message.orEmpty())?.groupValues?.get(1).orEmpty().trimEnd('.', ',')
        return ConfigError(ConfigError.Code.MalformedJson, path, message.ifBlank { "Malformed JSON" })
    }
}
