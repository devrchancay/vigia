package com.ramonchancay.vigia.core.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** JSON Merge Patch (RFC 7396): objects merge recursively, `null` removes a member, anything else replaces. */
object JsonMergePatch {
    fun apply(target: JsonElement, patch: JsonElement): JsonElement {
        if (patch !is JsonObject) return patch
        val result = (target as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        for ((key, value) in patch) {
            if (value is JsonNull) {
                result.remove(key)
            } else {
                result[key] = apply(result[key] ?: JsonNull, value)
            }
        }
        return JsonObject(result)
    }
}
