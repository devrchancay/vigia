package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val CLOCK_TIME_TAG = "clock_time"

/** Widest possible time in a monospace font; every "HH:mm:ss" value has the same width. */
private const val TIME_TEMPLATE = "88:88:88"
private val MIN_FONT_SIZE = 16.sp
private val MAX_FONT_SIZE = 200.sp

/**
 * Time and date, sized to fill the tile. [now] is read here, not by the caller, so each tick recomposes only
 * this widget. The font size is computed once per tile size from [TIME_TEMPLATE], so a tick only lays out one
 * line of text (auto-sizing on every change cost ~19 ms per frame on a Helio G25).
 */
@Composable
internal fun ClockWidget(now: State<Instant?>, zone: ZoneId, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }

    PanelTile(modifier = modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val timeStyle = MaterialTheme.typography.displayLarge
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val measurer = rememberTextMeasurer(cacheSize = 0)
                val density = LocalDensity.current
                val fontSize = remember(constraints, timeStyle, density) {
                    fitFontSize(measurer, timeStyle, constraints)
                }
                Text(
                    text = now.value?.atZone(zone)?.let(timeFormatter::format).orEmpty(),
                    style = timeStyle.copy(fontSize = fontSize, lineHeight = fontSize),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(CLOCK_TIME_TAG)
                )
            }
            // The date only changes at midnight; formatting it here does not recompose the Text when equal.
            Text(
                text = now.value?.atZone(zone)?.let(dateFormatter::format).orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** Largest font size (binary search, 1 sp precision) at which [TIME_TEMPLATE] fits in [constraints]. */
private fun fitFontSize(measurer: TextMeasurer, style: TextStyle, constraints: Constraints): TextUnit {
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return style.fontSize
    var low = MIN_FONT_SIZE.value
    var high = MAX_FONT_SIZE.value
    while (high - low > 1f) {
        val mid = (low + high) / 2
        val size = mid.sp
        val result = measurer.measure(
            text = TIME_TEMPLATE,
            style = style.copy(fontSize = size, lineHeight = size),
            maxLines = 1,
            softWrap = false
        )
        val fits = result.size.width <= constraints.maxWidth && result.size.height <= constraints.maxHeight
        if (fits) low = mid else high = mid
    }
    return low.sp
}
