package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.device.DeviceStatus
import com.ramonchancay.vigia.core.device.WifiStatus
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.core.model.WidgetTypes
import com.ramonchancay.vigia.feature.panel.R
import java.util.Locale

internal const val DEVICE_ROW_TAG = "device_row_"

/** Rows the `device` widget can show, by their `params.show` name ([WidgetTypes.DEVICE_ROWS]). */
internal enum class DeviceRow(val paramName: String) {
    Battery("battery"),
    BatteryTemp("batteryTemp"),
    Memory("memory"),
    Wifi("wifi");

    companion object {
        fun of(name: String): DeviceRow = entries.first { it.paramName == name }
    }
}

@Composable
internal fun DeviceWidget(widget: WidgetConfig, device: State<DeviceStatus?>, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.device(widget) }
    val rows = params.value?.show?.map(DeviceRow::of)
    if (rows == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val status = device.value
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: stringResource(R.string.device_title)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // One grid row: all values on one line, labels shortened; taller tiles list one value per line.
            val compact = maxHeight < COMPACT_BELOW
            val lines = rows.map { row -> deviceValue(row, status, compact) }
            if (compact) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    lines.forEach { line -> CompactDeviceItem(line) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { lines.forEach { DeviceLine(it) } }
            }
        }
    }
}

/** A label/value pair for one row; [warn] paints the value in the error color. */
internal data class DeviceValue(val row: DeviceRow, val label: String, val value: String?, val warn: Boolean = false)

@Composable
private fun deviceValue(row: DeviceRow, status: DeviceStatus?, compact: Boolean): DeviceValue = when (row) {
    DeviceRow.Battery -> DeviceValue(
        row = row,
        label = stringResource(if (compact) R.string.device_battery_short else R.string.device_battery),
        value = status?.battery?.let {
            val level = "${it.levelPercent}%"
            when {
                !it.isCharging -> level
                compact -> "$level ⚡"
                else -> stringResource(R.string.device_charging, level)
            }
        }
    )

    DeviceRow.BatteryTemp -> DeviceValue(
        row = row,
        label = stringResource(if (compact) R.string.device_battery_temp_short else R.string.device_battery_temp),
        value = status?.battery?.let { String.format(Locale.ROOT, "%.1f °C", it.temperatureCelsius) },
        warn = status?.battery?.isHot == true
    )

    DeviceRow.Memory -> DeviceValue(
        row = row,
        label = stringResource(if (compact) R.string.device_memory_short else R.string.device_memory),
        value = status?.memory?.let {
            String.format(
                Locale.ROOT,
                if (compact) "%.1f/%.1f GB" else "%.1f / %.1f GB",
                it.usedBytes.toGb(),
                it.totalBytes.toGb()
            )
        }
    )

    DeviceRow.Wifi -> DeviceValue(
        row = row,
        label = stringResource(R.string.device_wifi),
        value = status?.wifi?.let { wifi ->
            if (!wifi.isConnected) stringResource(R.string.device_wifi_off) else wifi.signalBars()
        }
    )
}

@Composable
private fun CompactDeviceItem(item: DeviceValue) {
    Row(
        modifier = Modifier.testTag(DEVICE_ROW_TAG + item.row.paramName),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            item.value ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun DeviceLine(item: DeviceValue) {
    val label = item.label
    val value = item.value
    val valueColor = if (item.warn) MaterialTheme.colorScheme.error else null
    Row(
        modifier = Modifier.fillMaxWidth().testTag(DEVICE_ROW_TAG + item.row.paramName),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun Long.toGb(): Double = this / BYTES_PER_GB

private const val BYTES_PER_GB = 1_073_741_824.0

/** "●●●○" style bars; "●" when connected but the signal is unknown. */
private fun WifiStatus.signalBars(): String {
    val level = signalLevel ?: return "●"
    return "●".repeat(level) + "○".repeat(WifiStatus.MAX_SIGNAL_LEVEL - level)
}
