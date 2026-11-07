package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                when (row) {
                    DeviceRow.Battery -> DeviceLine(
                        row = row,
                        label = stringResource(R.string.device_battery),
                        value = status?.battery?.let {
                            val level = "${it.levelPercent}%"
                            if (it.isCharging) stringResource(R.string.device_charging, level) else level
                        }
                    )

                    DeviceRow.BatteryTemp -> DeviceLine(
                        row = row,
                        label = stringResource(R.string.device_battery_temp),
                        value = status?.battery?.let { String.format(Locale.ROOT, "%.1f °C", it.temperatureCelsius) },
                        valueColor = if (status?.battery?.isHot == true) MaterialTheme.colorScheme.error else null
                    )

                    DeviceRow.Memory -> DeviceLine(
                        row = row,
                        label = stringResource(R.string.device_memory),
                        value = status?.memory?.let {
                            String.format(Locale.ROOT, "%.1f / %.1f GB", it.usedBytes.toGb(), it.totalBytes.toGb())
                        }
                    )

                    DeviceRow.Wifi -> DeviceLine(
                        row = row,
                        label = stringResource(R.string.device_wifi),
                        value = status?.wifi?.let { wifi ->
                            if (!wifi.isConnected) stringResource(R.string.device_wifi_off) else wifi.signalBars()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceLine(row: DeviceRow, label: String, value: String?, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(DEVICE_ROW_TAG + row.paramName),
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
