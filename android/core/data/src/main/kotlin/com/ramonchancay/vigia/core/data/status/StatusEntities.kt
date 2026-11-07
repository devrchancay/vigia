package com.ramonchancay.vigia.core.data.status

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.WorkflowRunStatus

/** One row per URL: only the latest state is kept, so the database does not grow on the eMMC. */
@Entity(tableName = "uptime_state")
internal data class UptimeStateEntity(
    @PrimaryKey val url: String,
    val up: Boolean,
    val statusCode: Int?,
    val latencyMs: Long?,
    val error: String?,
    val checkedAtMillis: Long,
    val changedAtMillis: Long
) {
    fun toModel() = UptimeStatus(url, up, statusCode, latencyMs, error, checkedAtMillis, changedAtMillis)

    companion object {
        fun of(s: UptimeStatus) =
            UptimeStateEntity(s.url, s.up, s.statusCode, s.latencyMs, s.error, s.checkedAtMillis, s.changedAtMillis)
    }
}

@Entity(tableName = "workflow_state")
internal data class WorkflowStateEntity(
    @PrimaryKey val key: String,
    val repo: String,
    val runName: String?,
    val branch: String?,
    val status: String?,
    val conclusion: String?,
    val htmlUrl: String?,
    val startedAtMillis: Long?,
    val updatedAtMillis: Long?,
    val error: String?,
    val checkedAtMillis: Long
) {
    fun toModel() = WorkflowRunStatus(
        key,
        repo,
        runName,
        branch,
        status,
        conclusion,
        htmlUrl,
        startedAtMillis,
        updatedAtMillis,
        error,
        checkedAtMillis
    )

    companion object {
        fun of(s: WorkflowRunStatus) = WorkflowStateEntity(
            s.key,
            s.repo,
            s.runName,
            s.branch,
            s.status,
            s.conclusion,
            s.htmlUrl,
            s.startedAtMillis,
            s.updatedAtMillis,
            s.error,
            s.checkedAtMillis
        )
    }
}
