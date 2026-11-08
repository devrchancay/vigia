package com.ramonchancay.vigia.core.data.status

import com.ramonchancay.vigia.core.model.MetricStatus
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

    val metrics: Flow<Map<String, MetricStatus>>

    /** Stores a reading and recomputes the change over the last day. */
    suspend fun recordMetric(key: String, value: Long, atMillis: Long)

    /** Keeps the last value and records why this reading failed. */
    suspend fun recordMetricError(key: String, error: String, atMillis: Long)

    /** Drops states no widget watches anymore. */
    suspend fun retainOnly(
        urls: Set<String>,
        workflowKeys: Set<String>,
        weatherKeys: Set<String> = emptySet(),
        metricKeys: Set<String> = emptySet()
    )
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

    override val metrics = dao.observeMetrics().map { rows -> rows.associate { it.key to it.toModel() } }

    override suspend fun recordMetric(key: String, value: Long, atMillis: Long) {
        dao.insertPoint(MetricPointEntity(key = key, value = value, atMillis = atMillis))
        dao.deletePointsBefore(atMillis - POINT_RETENTION_MILLIS)
        val dayAgo = dao.pointAtOrBefore(key, atMillis - DAY_MILLIS)
        dao.upsertMetric(
            MetricStateEntity(
                key,
                value,
                dayAgo?.let {
                    value - it.value
                },
                error = null,
                checkedAtMillis = atMillis
            )
        )
    }

    override suspend fun recordMetricError(key: String, error: String, atMillis: Long) {
        val previous = dao.metric(key)
        dao.upsertMetric(
            MetricStateEntity(key, previous?.value, previous?.changeLastDay, error = error, checkedAtMillis = atMillis)
        )
    }

    override suspend fun retainOnly(
        urls: Set<String>,
        workflowKeys: Set<String>,
        weatherKeys: Set<String>,
        metricKeys: Set<String>
    ) {
        dao.retainUptime(urls.toList())
        dao.retainWorkflows(workflowKeys.toList())
        dao.retainWeather(weatherKeys.toList())
        dao.retainMetricStates(metricKeys.toList())
        dao.retainMetricPoints(metricKeys.toList())
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        const val POINT_RETENTION_MILLIS = 8 * DAY_MILLIS
    }
}
