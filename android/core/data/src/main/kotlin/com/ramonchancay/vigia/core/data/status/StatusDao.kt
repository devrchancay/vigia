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
}
