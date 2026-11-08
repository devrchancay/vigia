package com.ramonchancay.vigia.feature.panel.widget

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.designsystem.theme.StatusColors
import com.ramonchancay.vigia.core.model.WeatherStatus
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.feature.panel.R
import kotlin.math.roundToInt

internal const val WEATHER_TEMP_TAG = "weather_temp"

/** WMO weather codes (as Open-Meteo returns them) grouped into what the tile shows. */
internal enum class Sky(val day: String, val night: String, @param:StringRes val label: Int) {
    Clear("☀", "☾", R.string.sky_clear),
    PartlyCloudy("⛅", "☁", R.string.sky_partly_cloudy),
    Cloudy("☁", "☁", R.string.sky_cloudy),
    Fog("🌫", "🌫", R.string.sky_fog),
    Drizzle("🌦", "🌧", R.string.sky_drizzle),
    Rain("🌧", "🌧", R.string.sky_rain),
    Snow("❄", "❄", R.string.sky_snow),
    Storm("⛈", "⛈", R.string.sky_storm);

    companion object {
        fun of(code: Int?): Sky? = when (code) {
            null -> null
            0 -> Clear
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            in 51..57 -> Drizzle
            in 61..67, in 80..82 -> Rain
            in 71..77, 85, 86 -> Snow
            in 95..99 -> Storm
            else -> Cloudy
        }
    }
}

@Composable
internal fun WeatherWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.weather(widget) }
    val value = params.value
    if (value == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val status = data.weather.value[WeatherStatus.keyOf(value.latitude, value.longitude)]
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: value.name) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            when {
                status?.temperatureC == null -> Text(
                    status?.error ?: stringResource(R.string.source_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status?.error != null) StatusColors.Warn else MaterialTheme.colorScheme.onSurfaceVariant
                )

                maxHeight < COMPACT_BELOW -> CompactWeather(status)

                else -> FullWeather(status)
            }
        }
    }
}

private fun Double.degrees(): String = "${roundToInt()}°"

@Composable
private fun FullWeather(status: WeatherStatus) {
    val sky = Sky.of(status.weatherCode)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            sky?.let { Text(if (status.isDay) it.day else it.night, style = MaterialTheme.typography.displaySmall) }
            Text(
                status.temperatureC?.degrees().orEmpty(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(WEATHER_TEMP_TAG)
            )
        }
        sky?.let { Text(stringResource(it.label), style = MaterialTheme.typography.titleMedium) }
        Text(
            details(status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactWeather(status: WeatherStatus) {
    val sky = Sky.of(status.weatherCode)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        sky?.let { Text(if (status.isDay) it.day else it.night, style = MaterialTheme.typography.titleLarge) }
        Text(
            status.temperatureC?.degrees().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(WEATHER_TEMP_TAG)
        )
        Text(
            details(status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** "feels 30° · ↑31° ↓23° · rain 40%", skipping what the API did not return. */
@Composable
private fun details(status: WeatherStatus): String = listOfNotNull(
    status.apparentTemperatureC?.let { stringResource(R.string.weather_feels, it.degrees()) },
    if (status.maxC != null && status.minC != null) "↑${status.maxC!!.degrees()} ↓${status.minC!!.degrees()}" else null,
    status.rainProbabilityMax?.let { stringResource(R.string.weather_rain, it) }
).joinToString(" · ")
