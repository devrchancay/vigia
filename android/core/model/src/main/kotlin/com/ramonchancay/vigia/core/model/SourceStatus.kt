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
