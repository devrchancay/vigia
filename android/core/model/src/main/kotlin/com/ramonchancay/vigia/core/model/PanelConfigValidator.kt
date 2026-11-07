package com.ramonchancay.vigia.core.model

/**
 * Semantic checks that the JSON schema alone cannot express. Returns every problem found, not just the
 * first, so a client can fix a config in one round trip. Widget `params` are validated by each widget type.
 */
object PanelConfigValidator {
    val GRID_SIZE_RANGE = 1..24
    val GAP_RANGE = 0..64
    const val MAX_WIDGETS = 64
    private val TIME_FORMAT = Regex("""([01]\d|2[0-3]):[0-5]\d""")
    private val ID_FORMAT = Regex("""[a-z0-9][a-z0-9_-]{0,63}""")

    fun validate(config: PanelConfig): List<ConfigError> = buildList {
        if (config.schemaVersion != PanelConfig.CURRENT_SCHEMA_VERSION) {
            add(
                ConfigError(
                    ConfigError.Code.UnsupportedSchemaVersion,
                    "schemaVersion",
                    "Unsupported schemaVersion ${config.schemaVersion}; expected ${PanelConfig.CURRENT_SCHEMA_VERSION}"
                )
            )
        }
        if (config.theme.palette !in ThemeConfig.PALETTES) {
            add(
                ConfigError(
                    ConfigError.Code.InvalidFormat,
                    "theme.palette",
                    "Unknown palette '${config.theme.palette}'; use one of ${ThemeConfig.PALETTES.joinToString()}"
                )
            )
        }
        validateGrid(config.grid)
        config.night?.let { validateNight(it) }
        if (config.widgets.size > MAX_WIDGETS) {
            add(
                ConfigError(
                    ConfigError.Code.OutOfRange,
                    "widgets",
                    "At most $MAX_WIDGETS widgets are allowed, got ${config.widgets.size}"
                )
            )
        }
        validateWidgets(config.widgets, config.grid)
    }

    private fun MutableList<ConfigError>.validateGrid(grid: GridConfig) {
        if (grid.columns !in GRID_SIZE_RANGE) add(outOfRange("grid.columns", grid.columns, GRID_SIZE_RANGE))
        if (grid.rows !in GRID_SIZE_RANGE) add(outOfRange("grid.rows", grid.rows, GRID_SIZE_RANGE))
        if (grid.gap !in GAP_RANGE) add(outOfRange("grid.gap", grid.gap, GAP_RANGE))
    }

    private fun MutableList<ConfigError>.validateNight(night: NightConfig) {
        if (!TIME_FORMAT.matches(night.from)) add(invalidTime("night.from", night.from))
        if (!TIME_FORMAT.matches(night.to)) add(invalidTime("night.to", night.to))
        if (night.brightness !in 0f..1f) {
            add(
                ConfigError(
                    ConfigError.Code.OutOfRange,
                    "night.brightness",
                    "Must be between 0 and 1, got ${night.brightness}"
                )
            )
        }
    }

    private fun MutableList<ConfigError>.validateWidgets(widgets: List<WidgetConfig>, grid: GridConfig) {
        val gridIsValid = grid.columns in GRID_SIZE_RANGE && grid.rows in GRID_SIZE_RANGE
        val seenIds = mutableSetOf<String>()
        widgets.forEachIndexed { index, widget ->
            val path = "widgets[$index]"
            if (!ID_FORMAT.matches(widget.id)) {
                add(
                    ConfigError(
                        ConfigError.Code.InvalidFormat,
                        "$path.id",
                        "Must be 1-64 chars of a-z, 0-9, '-' or '_', starting with a letter or digit"
                    )
                )
            } else if (!seenIds.add(widget.id)) {
                add(ConfigError(ConfigError.Code.DuplicateId, "$path.id", "Duplicate widget id '${widget.id}'"))
            }
            if (widget.type.isBlank()) {
                add(ConfigError(ConfigError.Code.InvalidFormat, "$path.type", "Must not be blank"))
            }
            WidgetParams.validate(widget).forEach { add(it.copy(path = "$path.${it.path}")) }
            validatePosition(widget.pos, grid, "$path.pos", checkBounds = gridIsValid)
        }
        if (gridIsValid) validateOverlaps(widgets)
    }

    private fun MutableList<ConfigError>.validatePosition(
        pos: WidgetPosition,
        grid: GridConfig,
        path: String,
        checkBounds: Boolean
    ) {
        if (pos.x < 0) add(outOfRange("$path.x", pos.x, 0 until grid.columns))
        if (pos.y < 0) add(outOfRange("$path.y", pos.y, 0 until grid.rows))
        if (pos.w < 1) add(outOfRange("$path.w", pos.w, 1..grid.columns))
        if (pos.h < 1) add(outOfRange("$path.h", pos.h, 1..grid.rows))
        if (!checkBounds || pos.x < 0 || pos.y < 0 || pos.w < 1 || pos.h < 1) return
        if (pos.x + pos.w > grid.columns) {
            add(
                ConfigError(
                    ConfigError.Code.OutOfGrid,
                    path,
                    "x + w = ${pos.x + pos.w} exceeds grid.columns = ${grid.columns}"
                )
            )
        }
        if (pos.y + pos.h > grid.rows) {
            add(
                ConfigError(
                    ConfigError.Code.OutOfGrid,
                    path,
                    "y + h = ${pos.y + pos.h} exceeds grid.rows = ${grid.rows}"
                )
            )
        }
    }

    private fun MutableList<ConfigError>.validateOverlaps(widgets: List<WidgetConfig>) {
        for (i in widgets.indices) {
            for (j in 0 until i) {
                if (widgets[i].pos.overlaps(widgets[j].pos)) {
                    add(
                        ConfigError(
                            ConfigError.Code.Overlap,
                            "widgets[$i].pos",
                            "Overlaps widget '${widgets[j].id}' (widgets[$j])"
                        )
                    )
                }
            }
        }
    }

    private fun WidgetPosition.overlaps(other: WidgetPosition): Boolean = w > 0 && h > 0 && other.w > 0 &&
        other.h > 0 && x < other.x + other.w && other.x < x + w && y < other.y + other.h && other.y < y + h

    private fun outOfRange(path: String, value: Int, range: IntRange) = ConfigError(
        ConfigError.Code.OutOfRange,
        path,
        "Must be between ${range.first} and ${range.last}, got $value"
    )

    private fun invalidTime(path: String, value: String) =
        ConfigError(ConfigError.Code.InvalidFormat, path, "Must be a 24h time \"HH:mm\", got \"$value\"")
}
