package com.ramonchancay.vigia.core.data.status

import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.WeatherStatus
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the sources found, keyed by URL (uptime) and by [WorkflowRunStatus.key]; read by widgets and `/status`. */
interface StatusRepository {
    val uptime: Flow<Map<String, UptimeStatus>>
    val workflows: Flow<Map<String, WorkflowRunStatus>>

    /** Stores a check result; keeps the previous change time while [UptimeStatus.up] does not flip. */
    suspend fun recordUptime(
        url: String,
        up: Boolean,
        statusCode: Int?,
        latencyMs: Long?,
        error: String?,
        checkedAtMillis: Long
    )

    suspend fun recordWorkflow(status: WorkflowRunStatus)

    val weather: Flow<Map<String, WeatherStatus>>

    /** Stores a weather reading; on an error keeps the last reading and only sets [WeatherStatus.error]. */
    suspend fun recordWeather(status: WeatherStatus)

    /** Drops states no widget watches anymore. */
    suspend fun retainOnly(urls: Set<String>, workflowKeys: Set<String>, weatherKeys: Set<String> = emptySet())
}

@Singleton
internal class RoomStatusRepository @Inject constructor(private val dao: StatusDao) : StatusRepository {
    override val uptime = dao.observeUptime().map { rows -> rows.associate { it.url to it.toModel() } }
    override val workflows = dao.observeWorkflows().map { rows -> rows.associate { it.key to it.toModel() } }

    override suspend fun recordUptime(
        url: String,
        up: Boolean,
        statusCode: Int?,
        latencyMs: Long?,
        error: String?,
        checkedAtMillis: Long
    ) {
        val previous = dao.uptime(url)
        val changedAt = if (previous != null && previous.up == up) previous.changedAtMillis else checkedAtMillis
        dao.upsertUptime(UptimeStateEntity(url, up, statusCode, latencyMs, error, checkedAtMillis, changedAt))
    }

    override suspend fun recordWorkflow(status: WorkflowRunStatus) = dao.upsertWorkflow(WorkflowStateEntity.of(status))

    override val weather = dao.observeWeather().map { rows -> rows.associate { it.key to it.toModel() } }

    override suspend fun recordWeather(status: WeatherStatus) {
        val previous = dao.weather(status.key)?.toModel()
        val merged = if (status.error != null &&
            previous != null
        ) {
            previous.copy(error = status.error, checkedAtMillis = status.checkedAtMillis)
        } else {
            status
        }
        dao.upsertWeather(WeatherStateEntity.of(merged))
    }

    override suspend fun retainOnly(urls: Set<String>, workflowKeys: Set<String>, weatherKeys: Set<String>) {
        dao.retainUptime(urls.toList())
        dao.retainWorkflows(workflowKeys.toList())
        dao.retainWeather(weatherKeys.toList())
    }
}
