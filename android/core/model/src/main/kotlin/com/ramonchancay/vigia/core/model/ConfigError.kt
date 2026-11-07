package com.ramonchancay.vigia.core.model

/**
 * A problem in a config document. [path] points at the offending field using JSON-path-like notation
 * (`widgets[2].pos.w`), so the API can report it as `{ "error": { "code", "message", "path" } }`.
 */
data class ConfigError(val code: Code, val path: String, val message: String) {
    enum class Code {
        MalformedJson,
        UnsupportedSchemaVersion,
        OutOfRange,
        InvalidFormat,
        DuplicateId,
        OutOfGrid,
        Overlap
    }
}
