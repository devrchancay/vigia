package com.ramonchancay.vigia.core.data.status

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface StatusDao {
    @Query("SELECT * FROM uptime_state")
    fun observeUptime(): Flow<List<UptimeStateEntity>>

    @Query("SELECT * FROM uptime_state WHERE url = :url")
    suspend fun uptime(url: String): UptimeStateEntity?

    @Upsert
    suspend fun upsertUptime(entity: UptimeStateEntity)

    @Query("DELETE FROM uptime_state WHERE url NOT IN (:urls)")
    suspend fun retainUptime(urls: List<String>)

    @Query("SELECT * FROM workflow_state")
    fun observeWorkflows(): Flow<List<WorkflowStateEntity>>

    @Upsert
    suspend fun upsertWorkflow(entity: WorkflowStateEntity)

    @Query("DELETE FROM workflow_state WHERE `key` NOT IN (:keys)")
    suspend fun retainWorkflows(keys: List<String>)

    @Query("SELECT * FROM weather_state")
    fun observeWeather(): Flow<List<WeatherStateEntity>>

    @Query("SELECT * FROM weather_state WHERE `key` = :key")
    suspend fun weather(key: String): WeatherStateEntity?

    @Upsert
    suspend fun upsertWeather(entity: WeatherStateEntity)

    @Query("DELETE FROM weather_state WHERE `key` NOT IN (:keys)")
    suspend fun retainWeather(keys: List<String>)

    @Query("SELECT * FROM metric_state")
    fun observeMetrics(): Flow<List<MetricStateEntity>>

    @Query("SELECT * FROM metric_state WHERE `key` = :key")
    suspend fun metric(key: String): MetricStateEntity?

    @Upsert
    suspend fun upsertMetric(entity: MetricStateEntity)

    @androidx.room.Insert
    suspend fun insertPoint(point: MetricPointEntity)

    /** The newest reading at or before [atMillis], or null. */
    @Query("SELECT * FROM metric_points WHERE `key` = :key AND atMillis <= :atMillis ORDER BY atMillis DESC LIMIT 1")
    suspend fun pointAtOrBefore(key: String, atMillis: Long): MetricPointEntity?

    @Query("DELETE FROM metric_points WHERE atMillis < :beforeMillis")
    suspend fun deletePointsBefore(beforeMillis: Long)

    @Query("DELETE FROM metric_state WHERE `key` NOT IN (:keys)")
    suspend fun retainMetricStates(keys: List<String>)

    @Query("DELETE FROM metric_points WHERE `key` NOT IN (:keys)")
    suspend fun retainMetricPoints(keys: List<String>)
}
