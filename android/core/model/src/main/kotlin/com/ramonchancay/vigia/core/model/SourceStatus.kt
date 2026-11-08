package com.ramonchancay.vigia.core.model

import kotlinx.serialization.Serializable

/**
 * Latest check of one URL. [changedAtMillis] is when [up] last flipped (or the first check), which the widget
 * shows as "down for 5 min". [error] is a short reason when the request itself failed.
 */
@Serializable
data class UptimeStatus(
    val url: String,
    val up: Boolean,
    val statusCode: Int? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val checkedAtMillis: Long,
    val changedAtMillis: Long
)

/** Latest GitHub Actions run for one [key] (repo + branch + workflow). [error] is set when fetching failed. */
@Serializable
data class WorkflowRunStatus(
    val key: String,
    val repo: String,
    val runName: String? = null,
    val branch: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    val htmlUrl: String? = null,
    val startedAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val error: String? = null,
    val checkedAtMillis: Long
) {
    companion object {
        /** Identifies one polled source; widgets watching the same repo, branch and workflow share it. */
        fun keyOf(repo: String, branch: String?, workflow: String?) =
            listOf(repo, branch.orEmpty(), workflow.orEmpty()).joinToString("|")
    }
}

/**
 * Current conditions for one point. [weatherCode] is the WMO code Open-Meteo returns; [rainProbabilityMax]
 * the highest precipitation probability for the rest of today, in percent.
 */
@Serializable
data class WeatherStatus(
    val key: String,
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
    val weatherCode: Int? = null,
    val isDay: Boolean = true,
    val maxC: Double? = null,
    val minC: Double? = null,
    val rainProbabilityMax: Int? = null,
    val error: String? = null,
    val checkedAtMillis: Long
) {
    companion object {
        /** Widgets watching the same spot (to ~1 km) share one poll. */
        fun keyOf(latitude: Double, longitude: Double): String =
            String.format(java.util.Locale.ROOT, "%.2f,%.2f", latitude, longitude)
    }
}

/**
 * A number tracked over time (e.g. X followers under key `x:<username>`). [changeLastDay] compares the latest
 * value with the one closest to 24 h earlier; null until there is a reading that old.
 */
@Serializable
data class MetricStatus(
    val key: String,
    val value: Long? = null,
    val changeLastDay: Long? = null,
    val error: String? = null,
    val checkedAtMillis: Long
) {
    companion object {
        fun xFollowersKey(username: String) = "x:${username.lowercase()}"
    }
}
