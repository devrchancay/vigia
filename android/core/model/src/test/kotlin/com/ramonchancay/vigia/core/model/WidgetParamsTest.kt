package com.ramonchancay.vigia.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetParamsTest {
    private fun widget(type: String, params: String) = WidgetConfig(
        id = "w",
        type = type,
        pos = WidgetPosition(0, 0, 1, 1),
        params = Json.parseToJsonElement(params).jsonObject
    )

    private fun paths(type: String, params: String) = WidgetParams.validate(widget(type, params)).map { it.path }

    @Test
    fun `valid uptime params decode with defaults`() {
        val decoded = WidgetParams.uptime(
            widget("uptime", """{"targets":[{"name":"site","url":"https://example.com"}]}""")
        )

        assertTrue(decoded.isValid)
        assertEquals(UptimeParams(listOf(UptimeTarget("site", "https://example.com"))), decoded.value)
    }

    @Test
    fun `uptime rejects bad urls, statuses, intervals and empty targets`() {
        assertEquals(
            listOf("params.targets[0].url", "params.targets[1].expectStatus", "params.intervalSec"),
            paths(
                "uptime",
                """{"targets":[{"name":"a","url":"ftp://x"},{"name":"b","url":"http://x.io","expectStatus":700}],"intervalSec":5}"""
            )
        )
        assertEquals(listOf("params.targets"), paths("uptime", """{"targets":[]}"""))
    }

    @Test
    fun `github actions validates repos, interval and secret name`() {
        assertEquals(
            listOf("params.repos[0]", "params.intervalSec", "params.tokenSecret"),
            paths("github_actions", """{"repos":["not-a-repo"],"intervalSec":10,"tokenSecret":"Bad Name"}""")
        )
        assertTrue(
            paths("github_actions", """{"repos":["owner/repo"],"branch":"main","tokenSecret":"github"}""").isEmpty()
        )
    }

    @Test
    fun `unknown keys and wrong types in params are malformed`() {
        val errors = WidgetParams.validate(widget("note", """{"txt":"hi"}"""))

        assertEquals(ConfigError.Code.MalformedJson, errors.single().code)
        assertEquals("params", errors.single().path)
    }

    @Test
    fun `device rows and note text are checked`() {
        assertEquals(listOf("params.show[1]"), paths("device", """{"show":["battery","gpu"]}"""))
        assertTrue(paths("device", "{}").isEmpty())
        assertEquals(listOf("params.text"), paths("note", """{"text":"   "}"""))
    }

    @Test
    fun `agents and feed params`() {
        assertEquals(AgentsParams(), WidgetParams.agents(widget("agents", "{}")).value)
        assertEquals(listOf("params.max"), paths("agents", """{"max":0}"""))
        assertEquals(listOf("params.sources[1]"), paths("feed", """{"sources":["jira","Hub!"]}"""))
        assertEquals(listOf("params.sources"), paths("feed", """{"sources":[]}"""))
    }

    @Test
    fun `types without params and unknown types are not checked`() {
        assertTrue(WidgetParams.validate(widget("clock", "{}")).isEmpty())
        assertTrue(
            WidgetParams.validate(
                WidgetConfig("x", "hologram", WidgetPosition(0, 0, 1, 1), params = JsonObject(mapOf()))
            ).isEmpty()
        )
    }
}
