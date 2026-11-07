package com.ramonchancay.vigia.feature.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.designsystem.theme.VigiaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val PANEL_TIME_TAG = "panel_time"

@Composable
internal fun PanelRoute(viewModel: PanelViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PanelScreen(uiState = uiState, onAction = viewModel::onAction)
}

@Composable
internal fun PanelScreen(
    uiState: PanelUiState,
    onAction: (PanelAction) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                PanelUiState.Loading -> CircularProgressIndicator()
                PanelUiState.Error -> PanelError(onRetry = { onAction(PanelAction.Retry) })
                is PanelUiState.Ready -> PanelReady(now = uiState.now, zone = zone)
            }
        }
    }
}

@Composable
private fun PanelReady(now: Instant, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val local = now.atZone(zone)

    // Landscape layout (the activity is locked to landscape): clock on the left, tiles on the right.
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = timeFormatter.format(local),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                // Shrinks the time to fit narrow screens instead of wrapping it onto two lines.
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 24.sp,
                    maxFontSize = MaterialTheme.typography.displayLarge.fontSize
                ),
                modifier = Modifier.testTag(PANEL_TIME_TAG)
            )
            Text(
                text = dateFormatter.format(local),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        PanelTile(title = stringResource(R.string.panel_status_title), modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.panel_status_no_config),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun PanelError(onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.panel_error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text(stringResource(R.string.panel_retry)) }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PanelReadyPreview() {
    VigiaTheme(darkTheme = true) {
        PanelScreen(
            uiState = PanelUiState.Ready(Instant.parse("2026-11-06T21:42:07Z")),
            onAction = {},
            zone = ZoneId.of("UTC")
        )
    }
}
