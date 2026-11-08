package com.ramonchancay.vigia.core.data.events

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface EventDao {
    /** Every stored event, newest first; readers drop expired ones with their own clock. */
    @Query("SELECT * FROM events ORDER BY receivedAtMillis DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE expiresAtMillis > :nowMillis ORDER BY receivedAtMillis DESC")
    suspend fun active(nowMillis: Long): List<EventEntity>

    @Upsert
    suspend fun upsert(events: List<EventEntity>)

    /** Keeps the newest [keep] events of [source]. */
    @Query(
        "DELETE FROM events WHERE source = :source AND id NOT IN " +
            "(SELECT id FROM events WHERE source = :source ORDER BY receivedAtMillis DESC LIMIT :keep)"
    )
    suspend fun trimSource(source: String, keep: Int)

    @Query("DELETE FROM events WHERE source = :source")
    suspend fun deleteSource(source: String): Int

    @Query("DELETE FROM events WHERE source = :source AND eventKey = :key")
    suspend fun deleteKey(source: String, key: String): Int

    @Query("DELETE FROM events WHERE expiresAtMillis <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long): Int

    @Transaction
    suspend fun insertAndTrim(events: List<EventEntity>, keepPerSource: Int) {
        upsert(events)
        events.map { it.source }.distinct().forEach { trimSource(it, keepPerSource) }
    }
}
