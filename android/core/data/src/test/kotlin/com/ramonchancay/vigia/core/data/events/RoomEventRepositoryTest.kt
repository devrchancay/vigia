package com.ramonchancay.vigia.core.data.events

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ramonchancay.vigia.core.data.status.StatusDatabase
import com.ramonchancay.vigia.core.model.EventLevel
import com.ramonchancay.vigia.core.model.PanelEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomEventRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        StatusDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()

    /** Mutable clock so tests can move time past a TTL. */
    private var nowMillis = 1_000_000L
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    }
    private val repository = RoomEventRepository(database.eventDao(), clock)

    @After
    fun tearDown() = database.close()

    @Test
    fun `same source and key replace the previous event`() = runTest {
        repository.push(listOf(PanelEvent("claude-code", "vigia: working", key = "s1", kind = "working")))
        repository.push(
            listOf(
                PanelEvent(
                    "claude-code",
                    "vigia: needs permission",
                    key = "s1",
                    kind = "waiting_permission",
                    level = EventLevel.Warn
                )
            )
        )

        val active = repository.active()
        assertEquals(1, active.size)
        assertEquals("waiting_permission", active.single().kind)
        assertEquals(EventLevel.Warn, active.single().level)
    }

    @Test
    fun `events without key accumulate and expire after their ttl`() = runTest {
        repository.push(
            listOf(PanelEvent("radar", "Lead A", ttlSec = 60), PanelEvent("radar", "Lead B", ttlSec = 3_600))
        )
        assertEquals(2, repository.active().size)

        nowMillis += 61_000
        assertEquals(listOf("Lead B"), repository.active().map { it.title })
        assertEquals(1, repository.deleteExpired())
    }

    @Test
    fun `keeps at most the newest events per source`() = runTest {
        (1..EventRepository.MAX_PER_SOURCE + 5).forEach {
            nowMillis += 1
            repository.push(listOf(PanelEvent("radar", "Lead $it")))
        }

        val active = repository.active()
        assertEquals(EventRepository.MAX_PER_SOURCE, active.size)
        assertEquals("Lead ${EventRepository.MAX_PER_SOURCE + 5}", active.first().title)
    }

    @Test
    fun `delete one key or a whole source`() = runTest {
        repository.push(
            listOf(
                PanelEvent("claude-code", "a", key = "s1"),
                PanelEvent("claude-code", "b", key = "s2"),
                PanelEvent("radar", "c")
            )
        )

        assertEquals(1, repository.delete("claude-code", "s1"))
        assertEquals(1, repository.delete("claude-code", null))
        assertEquals(listOf("radar"), repository.active().map { it.source })
    }

    @Test
    fun `stores at as epoch millis`() = runTest {
        val stored = repository.push(
            listOf(PanelEvent("calendar", "Standup", at = "2026-11-07T10:00:00-05:00"))
        ).single()

        assertEquals(Instant.parse("2026-11-07T15:00:00Z").toEpochMilli(), stored.atMillis)
    }
}
