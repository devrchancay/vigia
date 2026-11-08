package com.ramonchancay.vigia.core.data.events

import com.ramonchancay.vigia.core.model.PanelEvent
import com.ramonchancay.vigia.core.model.StoredEvent
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Pushed events (`POST /events`) that feed the `agents` and `feed` widgets. */
interface EventRepository {
    /** Stored events, newest first, including expired ones until the pruner runs: filter with [StoredEvent.expiresAtMillis]. */
    val events: Flow<List<StoredEvent>>

    /** Stores validated events; same source and key replace the previous one. Returns them as stored. */
    suspend fun push(events: List<PanelEvent>): List<StoredEvent>

    suspend fun active(): List<StoredEvent>

    /** Deletes one keyed event, or every event of [source] when [key] is null. Returns how many were removed. */
    suspend fun delete(source: String, key: String?): Int

    suspend fun deleteExpired(): Int

    companion object {
        /** Older events of a source beyond this are dropped, so a chatty script cannot fill the eMMC. */
        const val MAX_PER_SOURCE = 100
    }
}

@Singleton
internal class RoomEventRepository @Inject constructor(private val dao: EventDao, private val clock: Clock) :
    EventRepository {
    override val events: Flow<List<StoredEvent>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun push(events: List<PanelEvent>): List<StoredEvent> {
        val now = clock.millis()
        val entities = events.map { event ->
            EventEntity(
                id = event.key?.let { "${event.source}|$it" } ?: "${event.source}|${UUID.randomUUID()}",
                source = event.source,
                eventKey = event.key,
                kind = event.kind,
                title = event.title.trim(),
                body = event.body?.trim(),
                level = event.level.name,
                url = event.url,
                atMillis = event.at?.let { PanelEvent.parseInstant(it)?.toEpochMilli() },
                receivedAtMillis = now,
                expiresAtMillis = now + event.ttlSec * MILLIS_PER_SECOND
            )
        }
        dao.insertAndTrim(entities, EventRepository.MAX_PER_SOURCE)
        return entities.map { it.toModel() }
    }

    override suspend fun active(): List<StoredEvent> = dao.active(clock.millis()).map { it.toModel() }

    override suspend fun delete(source: String, key: String?): Int = if (key ==
        null
    ) {
        dao.deleteSource(source)
    } else {
        dao.deleteKey(source, key)
    }

    override suspend fun deleteExpired(): Int = dao.deleteExpired(clock.millis())

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
