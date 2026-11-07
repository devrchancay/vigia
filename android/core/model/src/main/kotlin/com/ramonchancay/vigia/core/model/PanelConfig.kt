package com.ramonchancay.vigia.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The whole panel, as described by the JSON config document (see docs/SPEC.md, section 4). */
@Serializable
data class PanelConfig(
    val schemaVersion: Int,
    val theme: ThemeConfig = ThemeConfig(),
    val grid: GridConfig = GridConfig(),
    val night: NightConfig? = null,
    val widgets: List<WidgetConfig> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.Dark,
    val palette: String = "ember",
    val background: Background = Background.Solid
)

@Serializable
enum class ThemeMode {
    @SerialName("dark")
    Dark,

    @SerialName("light")
    Light
}

@Serializable
enum class Background {
    @SerialName("solid")
    Solid,

    @SerialName("gradient")
    Gradient
}

/** Landscape by default: the panel is locked to landscape. */
@Serializable
data class GridConfig(val columns: Int = 8, val rows: Int = 4, val gap: Int = 8)

/** Night window in local time ("HH:mm"); it may cross midnight (e.g. 23:00 to 07:00). */
@Serializable
data class NightConfig(val from: String, val to: String, val brightness: Float)

/**
 * One tile on the grid. [type] is kept as a string so an unknown type still parses and is rendered as an
 * error tile instead of rejecting the whole config. [params] are decoded by the widget that owns [type].
 */
@Serializable
data class WidgetConfig(
    val id: String,
    val type: String,
    val pos: WidgetPosition,
    val title: String? = null,
    val params: JsonObject = JsonObject(emptyMap())
)

/** Cell coordinates on the grid: top-left corner ([x], [y]) and size in cells ([w], [h]). */
@Serializable
data class WidgetPosition(val x: Int, val y: Int, val w: Int, val h: Int)
