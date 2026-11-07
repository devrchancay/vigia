package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.designsystem.theme.StatusColors
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import com.ramonchancay.vigia.feature.panel.R

internal const val GITHUB_ROW_TAG = "github_row_"

/** One row per repo: result symbol, repo, run name, and duration (finished) or elapsed time (running). */
@Composable
internal fun GithubActionsWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.githubActions(widget) }
    val value = params.value
    if (value == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val runs = data.workflows.value
    val minute = data.minute.value
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: stringResource(R.string.github_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            value.repos.forEach { repo ->
                RunRow(repo, runs[WorkflowRunStatus.keyOf(repo, value.branch, value.workflow)], minute)
            }
        }
    }
}

@Composable
private fun RunRow(repo: String, run: WorkflowRunStatus?, nowMillis: Long) {
    val (symbol, color) = when {
        run == null || (run.status == null && run.error == null) -> "○" to StatusColors.Unknown
        run.status == null -> "!" to StatusColors.Warn
        run.status != "completed" -> "●" to StatusColors.Warn
        run.conclusion == "success" -> "✓" to StatusColors.Ok
        run.conclusion in setOf("skipped", "neutral", "cancelled") -> "–" to StatusColors.Unknown
        else -> "✗" to StatusColors.Error
    }
    val detail = when {
        run == null -> stringResource(R.string.source_pending)
        run.status == null -> run.error ?: stringResource(R.string.github_no_runs)
        run.status != "completed" -> run.startedAtMillis?.let { elapsed(it, nowMillis) }.orEmpty()
        else -> duration(run.startedAtMillis, run.updatedAtMillis)
    }
    Row(
        modifier = Modifier.fillMaxWidth().testTag(GITHUB_ROW_TAG + repo),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = symbol, style = MaterialTheme.typography.titleMedium, color = color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repo.substringAfter('/'),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            run?.runName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = if (run?.error != null) StatusColors.Warn else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** "3m 57s" for a finished run; empty when GitHub did not report both times. */
private fun duration(startedAtMillis: Long?, updatedAtMillis: Long?): String {
    if (startedAtMillis == null || updatedAtMillis == null) return ""
    val seconds = ((updatedAtMillis - startedAtMillis) / 1_000).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
