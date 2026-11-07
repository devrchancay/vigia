package com.ramonchancay.vigia.core.model

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelConfigParserTest {
    @Test
    fun `parses a full config`() {
        val result = PanelConfigParser.parse(
            """
            {
              "schemaVersion": 1,
              "theme": { "mode": "light", "palette": "teal", "background": "gradient" },
              "grid": { "columns": 6, "rows": 3, "gap": 4 },
              "night": { "from": "23:00", "to": "07:00", "brightness": 0.05 },
              "widgets": [
                { "id": "clock", "type": "clock", "pos": { "x": 0, "y": 0, "w": 3, "h": 2 } },
                { "id": "todo", "type": "note", "title": "Today", "pos": { "x": 3, "y": 0, "w": 3, "h": 1 },
                  "params": { "text": "Ship M1" } }
              ]
            }
            """.trimIndent()
        )

        val config = (result as ConfigParseResult.Valid).config
        assertEquals(ThemeConfig(ThemeMode.Light, "teal", Background.Gradient), config.theme)
        assertEquals(GridConfig(columns = 6, rows = 3, gap = 4), config.grid)
        assertEquals(NightConfig("23:00", "07:00", 0.05f), config.night)
        assertEquals(listOf("clock", "todo"), config.widgets.map { it.id })
        assertEquals("Today", config.widgets[1].title)
        assertEquals(JsonPrimitive("Ship M1"), config.widgets[1].params["text"])
    }

    @Test
    fun `applies landscape defaults when only the schema version is given`() {
        val config = (PanelConfigParser.parse("""{ "schemaVersion": 1 }""") as ConfigParseResult.Valid).config

        assertEquals(PanelConfig(schemaVersion = 1), config)
        assertEquals(GridConfig(columns = 8, rows = 4, gap = 8), config.grid)
    }

    @Test
    fun `keeps unknown widget types so they can be rendered as error tiles`() {
        val result = PanelConfigParser.parse(
            """{ "schemaVersion": 1, "widgets": [ { "id": "x", "type": "hologram", "pos": { "x": 0, "y": 0, "w": 1, "h": 1 } } ] }"""
        )

        assertEquals("hologram", (result as ConfigParseResult.Valid).config.widgets.single().type)
    }

    @Test
    fun `rejects unknown keys and points at them`() {
        val result = PanelConfigParser.parse("""{ "schemaVersion": 1, "grid": { "colums": 4 } }""")

        val error = (result as ConfigParseResult.Invalid).errors.single()
        assertEquals(ConfigError.Code.MalformedJson, error.code)
        assertEquals("grid", error.path)
        assertTrue(error.message, error.message.contains("colums"))
    }

    @Test
    fun `reports a missing required field`() {
        val result = PanelConfigParser.parse(
            """{ "schemaVersion": 1, "widgets": [ { "id": "clock", "pos": { "x": 0, "y": 0, "w": 1, "h": 1 } } ] }"""
        )

        val error = (result as ConfigParseResult.Invalid).errors.single()
        assertEquals(ConfigError.Code.MalformedJson, error.code)
        assertTrue(error.message, error.message.contains("type"))
    }

    @Test
    fun `reports an unknown enum value`() {
        val result = PanelConfigParser.parse("""{ "schemaVersion": 1, "theme": { "mode": "neon" } }""")

        val error = (result as ConfigParseResult.Invalid).errors.single()
        assertEquals(ConfigError.Code.MalformedJson, error.code)
        assertEquals("theme.mode", error.path)
    }

    @Test
    fun `reports text that is not JSON`() {
        val result = PanelConfigParser.parse("not json")

        assertEquals(ConfigError.Code.MalformedJson, (result as ConfigParseResult.Invalid).errors.single().code)
    }

    @Test
    fun `runs semantic validation after parsing`() {
        val result = PanelConfigParser.parse("""{ "schemaVersion": 2 }""")

        assertEquals(
            ConfigError.Code.UnsupportedSchemaVersion,
            (result as ConfigParseResult.Invalid).errors.single().code
        )
    }

    @Test
    fun `the example config in docs SPEC section 4 is valid`() {
        // Keep in sync with docs/SPEC.md: the documented example must always parse and validate.
        val result = PanelConfigParser.parse(
            """
            {
              "schemaVersion": 1,
              "theme": { "mode": "dark", "palette": "ember", "background": "gradient" },
              "grid": { "columns": 8, "rows": 4, "gap": 8 },
              "night": { "from": "23:00", "to": "07:00", "brightness": 0.05 },
              "widgets": [
                { "id": "clock", "type": "clock", "pos": { "x": 0, "y": 0, "w": 3, "h": 2 } },
                { "id": "device", "type": "device", "pos": { "x": 0, "y": 2, "w": 3, "h": 1 },
                  "params": { "show": ["battery", "batteryTemp", "memory", "wifi"] } },
                { "id": "agents", "type": "agents", "title": "Claude Code", "pos": { "x": 0, "y": 3, "w": 3, "h": 1 } },
                { "id": "sites", "type": "uptime", "title": "Sites", "pos": { "x": 3, "y": 0, "w": 2, "h": 2 },
                  "params": { "intervalSec": 60, "targets": [
                    { "name": "website", "url": "https://example.com" },
                    { "name": "api", "url": "https://api.example.com/health", "expectStatus": 200 } ] } },
                { "id": "ci", "type": "github_actions", "title": "CI", "pos": { "x": 5, "y": 0, "w": 3, "h": 2 },
                  "params": { "repos": ["owner/repo"], "branch": "main", "intervalSec": 60, "tokenSecret": "github" } },
                { "id": "inbox", "type": "feed", "title": "Upwork & leads", "pos": { "x": 3, "y": 2, "w": 5, "h": 2 },
                  "params": { "sources": ["upwork", "radar"], "max": 5 } }
              ]
            }
            """.trimIndent()
        )

        assertTrue(result.toString(), result is ConfigParseResult.Valid)
        assertEquals(6, (result as ConfigParseResult.Valid).config.widgets.size)
    }

    @Test
    fun `round trips through encode`() {
        val config = PanelConfig(
            schemaVersion = 1,
            widgets = listOf(WidgetConfig("clock", "clock", WidgetPosition(0, 0, 4, 2)))
        )

        assertEquals(ConfigParseResult.Valid(config), PanelConfigParser.parse(PanelConfigParser.encode(config)))
    }
}
