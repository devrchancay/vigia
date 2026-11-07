package com.ramonchancay.vigia.core.server.api

import com.ramonchancay.vigia.core.model.ConfigError
import kotlinx.serialization.Serializable

/** Error body for every failure: `error` is the first problem, `errors` all of them (config validation). */
@Serializable
data class ApiErrorBody(val error: ApiError, val errors: List<ApiError> = listOf(error))

@Serializable
data class ApiError(val code: String, val message: String, val path: String = "")

@Serializable
data class HealthBody(val status: String, val version: String, val uptimeSec: Long)

@Serializable
data class ValidationBody(val valid: Boolean)

internal fun ConfigError.toApiError() = ApiError(code = code.name.toSnakeCase(), message = message, path = path)

internal fun List<ConfigError>.toBody() = ApiErrorBody(error = first().toApiError(), errors = map { it.toApiError() })

private fun String.toSnakeCase(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
