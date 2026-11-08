package com.ramonchancay.vigia.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelEventTest {
    @Test
    fun `a minimal event is valid with defaults`() {
        val event = PanelEvent(source = "claude-code", title = "vigia: working")

        assertTrue(PanelEvent.validate(event).isEmpty())
        assertEquals(EventLevel.Info, event.level)
        assertEquals(24 * 3600, event.ttlSec)
    }

    @Test
    fun `reports every bad field with the batch path`() {
        val event = PanelEvent(
            source = "Claude Code",
            title = " ",
            kind = "Waiting-Permission",
            ttlSec = 5,
            url = "javascript:alert(1)",
            at = "tomorrow"
        )

        assertEquals(
            listOf(
                "events[3].source",
                "events[3].title",
                "events[3].kind",
                "events[3].ttlSec",
                "events[3].url",
                "events[3].at"
            ),
            PanelEvent.validate(event, prefix = "events[3]").map { it.path }
        )
    }

    @Test
    fun `parses instants with zone or offset`() {
        assertEquals(Instant.parse("2026-11-07T15:00:00Z"), PanelEvent.parseInstant("2026-11-07T10:00:00-05:00"))
        assertEquals(Instant.parse("2026-11-07T15:00:00Z"), PanelEvent.parseInstant("2026-11-07T15:00:00Z"))
    }
}
