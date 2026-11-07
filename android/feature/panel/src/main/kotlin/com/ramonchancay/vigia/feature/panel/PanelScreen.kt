package com.ramonchancay.vigia.feature.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramonchancay.vigia.core.designsystem.theme.VigiaPalette
import com.ramonchancay.vigia.core.designsystem.theme.VigiaTheme
import com.ramonchancay.vigia.core.device.BatteryStatus
import com.ramonchancay.vigia.core.device.DeviceStatus
import com.ramonchancay.vigia.core.device.MemoryStatus
import com.ramonchancay.vigia.core.device.WifiStatus
import com.ramonchancay.vigia.core.model.Background
import com.ramonchancay.vigia.core.model.GridConfig
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.ThemeMode
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetPosition
import com.ramonchancay.vigia.feature.panel.grid.PanelGrid
import com.ramonchancay.vigia.feature.panel.widget.PanelWidget
import com.ramonchancay.vigia.feature.panel.widget.WidgetData
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun PanelRoute(viewModel: PanelViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Passed down as State (not the value) so each value is read only inside the widget that needs it.
    val now = viewModel.now.collectAsStateWithLifecycle()
    val device = viewModel.device.collectAsStateWithLifecycle()
    PanelScreen(uiState = uiState, now = now, device = device, onAction = viewModel::onAction)
}

@Composable
internal fun PanelScreen(
    uiState: PanelUiState,
    now: State<Instant?>,
    device: State<DeviceStatus?>,
    onAction: (PanelAction) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val data = remember(now, device, zone) { WidgetData(now = now, device = device, zone = zone) }
    when (uiState) {
        PanelUiState.Loading -> CenteredBox(modifier) { CircularProgressIndicator() }

        PanelUiState.Error -> CenteredBox(modifier) { PanelError(onRetry = { onAction(PanelAction.Retry) }) }

        is PanelUiState.Ready -> VigiaTheme(
            darkTheme = uiState.config.theme.mode == ThemeMode.Dark,
            palette = VigiaPalette.fromConfigName(uiState.config.theme.palette)
        ) {
            PanelReady(config = uiState.config, data = data, modifier = modifier)
        }
    }
}

@Composable
private fun PanelReady(config: PanelConfig, data: WidgetData, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when (config.theme.background) {
        Background.Solid -> Brush.linearGradient(listOf(colors.background, colors.background))
        Background.Gradient -> Brush.verticalGradient(listOf(colors.background, colors.surfaceContainer))
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding()
            .padding(config.grid.gap.dp)
    ) {
        if (config.widgets.isEmpty()) {
            Text(
                text = stringResource(R.string.panel_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            PanelGrid(grid = config.grid, widgets = config.widgets, modifier = Modifier.fillMaxSize()) { widget ->
                PanelWidget(widget = widget, data = data)
            }
        }
    }
}

@Composable
private fun CenteredBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun PanelError(onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.panel_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.panel_retry)) }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PanelReadyPreview() {
    val config = PanelConfig(
        schemaVersion = 1,
        grid = GridConfig(columns = 8, rows = 4),
        widgets = listOf(
            WidgetConfig("clock", "clock", WidgetPosition(0, 0, 5, 2)),
            WidgetConfig("device", "device", WidgetPosition(5, 0, 3, 2)),
            WidgetConfig(
                "welcome",
                "note",
                WidgetPosition(0, 2, 8, 2),
                title = "Vigía",
                params = JsonObject(mapOf("text" to JsonPrimitive("Change this panel remotely.")))
            )
        )
    )
    val now = remember { mutableStateOf<Instant?>(Instant.parse("2026-11-06T21:42:07Z")) }
    val device = remember {
        mutableStateOf<DeviceStatus?>(
            DeviceStatus(
                battery = BatteryStatus(levelPercent = 80, temperatureCelsius = 31.5f, isCharging = true),
                memory = MemoryStatus(availableBytes = 1_200_000_000, totalBytes = 3_000_000_000),
                wifi = WifiStatus(isConnected = true, signalLevel = 3)
            )
        )
    }
    PanelScreen(uiState = PanelUiState.Ready(config), now = now, device = device, onAction = {
    }, zone = ZoneId.of("UTC"))
}
