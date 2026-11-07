package com.ramonchancay.vigia.feature.sources

import com.ramonchancay.vigia.core.model.ConfigParseResult
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.PanelConfigParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePlanTest {
    private fun config(json: String): PanelConfig = (PanelConfigParser.parse(json) as ConfigParseResult.Valid).config

    @Test
    fun `coalesces sources shared by widgets and keeps the shortest interval`() {
        val plan = SourcePlan.from(
            config(
                """
                {"schemaVersion":1,"widgets":[
                  {"id":"a","type":"uptime","pos":{"x":0,"y":0,"w":2,"h":2},"params":{"intervalSec":120,"targets":[
                    {"name":"site","url":"https://example.com"},{"name":"api","url":"https://api.example.com/health","expectStatus":204}]}},
                  {"id":"b","type":"uptime","pos":{"x":2,"y":0,"w":2,"h":2},"params":{"intervalSec":30,"targets":[
                    {"name":"site again","url":"https://example.com"}]}},
                  {"id":"c","type":"github_actions","pos":{"x":4,"y":0,"w":2,"h":2},"params":{"repos":["owner/repo","owner/other"],"branch":"main","intervalSec":90,"tokenSecret":"github"}},
                  {"id":"d","type":"github_actions","pos":{"x":6,"y":0,"w":2,"h":2},"params":{"repos":["owner/repo"],"branch":"main","intervalSec":60}}
                ]}
                """.trimIndent()
            )
        )

        assertEquals(UptimeJob("https://example.com", null, 30), plan.uptime["https://example.com"])
        assertEquals(204, plan.uptime.getValue("https://api.example.com/health").expectStatus)
        assertEquals(2, plan.github.size)
        val shared = plan.github.getValue("owner/repo|main|")
        assertEquals(60, shared.intervalSec)
        assertEquals("github", shared.tokenSecret)
    }

    @Test
    fun `widgets without sources produce an empty plan`() {
        assertEquals(SourcePlan(emptyMap(), emptyMap()), SourcePlan.from(PanelConfig(schemaVersion = 1)))
    }

    @Test
    fun `delay backs off up to four times and jitters by ten percent`() {
        assertEquals(54_000L, SourcePlan.nextDelayMillis(60, consecutiveFailures = 0, randomFraction = 0.0))
        assertEquals(66_000L, SourcePlan.nextDelayMillis(60, consecutiveFailures = 0, randomFraction = 1.0))
        assertEquals(120_000L, SourcePlan.nextDelayMillis(60, consecutiveFailures = 1, randomFraction = 0.5))
        assertEquals(240_000L, SourcePlan.nextDelayMillis(60, consecutiveFailures = 2, randomFraction = 0.5))
        assertEquals(240_000L, SourcePlan.nextDelayMillis(60, consecutiveFailures = 9, randomFraction = 0.5))
    }
}
