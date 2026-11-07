package com.ramonchancay.vigia.feature.panel.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.model.GridConfig
import com.ramonchancay.vigia.core.model.WidgetConfig
import kotlin.math.roundToInt

/**
 * Places each widget on its cells of a [grid]. Cell size is derived from the available space, so the same
 * config scales to any screen. Widgets are assumed valid (inside the grid, no overlaps): the config is
 * validated before it gets here.
 */
@Composable
fun PanelGrid(
    grid: GridConfig,
    widgets: List<WidgetConfig>,
    modifier: Modifier = Modifier,
    widgetContent: @Composable (WidgetConfig) -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            widgets.forEach { widget ->
                // One root node per widget, keyed by id so a widget keeps its state when others change.
                key(widget.id) { Box { widgetContent(widget) } }
            }
        }
    ) { measurables, constraints ->
        val gap = grid.gap.dp.roundToPx()
        val cellWidth = (constraints.maxWidth - gap * (grid.columns - 1)).toFloat() / grid.columns
        val cellHeight = (constraints.maxHeight - gap * (grid.rows - 1)).toFloat() / grid.rows

        val placeables = measurables.mapIndexed { index, measurable ->
            val pos = widgets[index].pos
            val width = (cellWidth * pos.w + gap * (pos.w - 1)).roundToInt().coerceAtLeast(0)
            val height = (cellHeight * pos.h + gap * (pos.h - 1)).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(width, height))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val pos = widgets[index].pos
                placeable.place(
                    x = ((cellWidth + gap) * pos.x).roundToInt(),
                    y = ((cellHeight + gap) * pos.y).roundToInt()
                )
            }
        }
    }
}
