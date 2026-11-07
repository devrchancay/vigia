package com.ramonchancay.vigia.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelConfigValidatorTest {
    private fun widget(id: String, x: Int, y: Int, w: Int = 1, h: Int = 1, type: String = "clock") =
        WidgetConfig(id = id, type = type, pos = WidgetPosition(x, y, w, h))

    private fun config(vararg widgets: WidgetConfig, grid: GridConfig = GridConfig(), night: NightConfig? = null) =
        PanelConfig(schemaVersion = 1, grid = grid, night = night, widgets = widgets.toList())

    private fun errorsOf(config: PanelConfig) = PanelConfigValidator.validate(config).map { it.code to it.path }

    @Test
    fun `a valid config has no errors`() {
        val valid = config(
            widget("clock", x = 0, y = 0, w = 4, h = 2),
            widget("device", x = 4, y = 0, w = 4, h = 2),
            widget("todo", x = 0, y = 2, w = 8, h = 2),
            night = NightConfig("23:00", "07:00", 0.05f)
        )

        assertEquals(emptyList<ConfigError>(), PanelConfigValidator.validate(valid))
    }

    @Test
    fun `rejects an unknown palette`() {
        val errors = errorsOf(PanelConfig(schemaVersion = 1, theme = ThemeConfig(palette = "neon")))

        assertEquals(listOf(ConfigError.Code.InvalidFormat to "theme.palette"), errors)
    }

    @Test
    fun `rejects grid sizes and gap out of range`() {
        val errors = errorsOf(config(grid = GridConfig(columns = 0, rows = 25, gap = 65)))

        assertEquals(
            listOf(
                ConfigError.Code.OutOfRange to "grid.columns",
                ConfigError.Code.OutOfRange to "grid.rows",
                ConfigError.Code.OutOfRange to "grid.gap"
            ),
            errors
        )
    }

    @Test
    fun `rejects malformed night times and brightness`() {
        val errors = errorsOf(config(night = NightConfig("24:00", "7:00", 1.5f)))

        assertEquals(
            listOf(
                ConfigError.Code.InvalidFormat to "night.from",
                ConfigError.Code.InvalidFormat to "night.to",
                ConfigError.Code.OutOfRange to "night.brightness"
            ),
            errors
        )
    }

    @Test
    fun `rejects invalid and duplicate widget ids`() {
        val errors = errorsOf(config(widget("Clock!", 0, 0), widget("a", 1, 0), widget("a", 2, 0)))

        assertEquals(
            listOf(
                ConfigError.Code.InvalidFormat to "widgets[0].id",
                ConfigError.Code.DuplicateId to "widgets[2].id"
            ),
            errors
        )
    }

    @Test
    fun `rejects a blank type`() {
        assertEquals(
            listOf(ConfigError.Code.InvalidFormat to "widgets[0].type"),
            errorsOf(config(widget("a", 0, 0, type = " ")))
        )
    }

    @Test
    fun `rejects negative coordinates and empty sizes`() {
        val errors = errorsOf(config(widget("a", x = -1, y = -1, w = 0, h = 0)))

        assertEquals(
            listOf(
                ConfigError.Code.OutOfRange to "widgets[0].pos.x",
                ConfigError.Code.OutOfRange to "widgets[0].pos.y",
                ConfigError.Code.OutOfRange to "widgets[0].pos.w",
                ConfigError.Code.OutOfRange to "widgets[0].pos.h"
            ),
            errors
        )
    }

    @Test
    fun `rejects widgets that leave the grid`() {
        val errors = PanelConfigValidator.validate(config(widget("wide", x = 6, y = 3, w = 3, h = 2)))

        assertEquals(listOf(ConfigError.Code.OutOfGrid, ConfigError.Code.OutOfGrid), errors.map { it.code })
        assertTrue(errors.all { it.path == "widgets[0].pos" })
        assertTrue(errors[0].message, errors[0].message.contains("grid.columns = 8"))
    }

    @Test
    fun `rejects overlapping widgets and names the other one`() {
        val errors = PanelConfigValidator.validate(
            config(widget("clock", 0, 0, w = 4, h = 2), widget("note", 3, 1, w = 2, h = 2))
        )

        val overlap = errors.single()
        assertEquals(ConfigError.Code.Overlap, overlap.code)
        assertEquals("widgets[1].pos", overlap.path)
        assertTrue(overlap.message, overlap.message.contains("'clock'"))
    }

    @Test
    fun `adjacent widgets do not overlap`() {
        assertEquals(
            emptyList<ConfigError>(),
            PanelConfigValidator.validate(config(widget("a", 0, 0, w = 4), widget("b", 4, 0, w = 4)))
        )
    }

    @Test
    fun `skips bounds and overlap checks when the grid itself is invalid`() {
        val errors = errorsOf(config(widget("a", 0, 0, w = 5), widget("b", 0, 0), grid = GridConfig(columns = 0)))

        assertEquals(listOf(ConfigError.Code.OutOfRange to "grid.columns"), errors)
    }

    @Test
    fun `params errors carry the full widget path`() {
        val note = WidgetConfig(
            id = "todo",
            type = "note",
            pos = WidgetPosition(0, 0, 1, 1),
            params = kotlinx.serialization.json.JsonObject(
                mapOf("text" to kotlinx.serialization.json.JsonPrimitive(" "))
            )
        )

        assertEquals(listOf(ConfigError.Code.InvalidFormat to "widgets[0].params.text"), errorsOf(config(note)))
    }

    @Test
    fun `limits the number of widgets`() {
        val many = (0 until PanelConfigValidator.MAX_WIDGETS + 1).map { widget("w$it", x = 0, y = 0) }
        val errors = PanelConfigValidator.validate(PanelConfig(schemaVersion = 1, widgets = many))

        assertTrue(errors.any { it.code == ConfigError.Code.OutOfRange && it.path == "widgets" })
    }
}
