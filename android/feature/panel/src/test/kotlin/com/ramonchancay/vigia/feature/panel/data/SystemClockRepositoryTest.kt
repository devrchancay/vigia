package com.ramonchancay.vigia.feature.panel.data

import app.cash.turbine.test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemClockRepositoryTest {
    @Test
    fun `waits until the next second boundary between ticks`() = runTest {
        val start = Instant.parse("2026-11-06T21:42:07.250Z")
        val clock = Clock.fixed(start, ZoneOffset.UTC)
        val repository = SystemClockRepository(clock, StandardTestDispatcher(testScheduler))

        repository.ticks().test {
            assertEquals(start, awaitItem())
            awaitItem()
            assertEquals(750L, currentTime)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
