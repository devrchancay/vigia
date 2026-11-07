package com.ramonchancay.vigia.feature.sources

import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.core.model.WidgetTypes
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import kotlin.math.min
import kotlin.math.roundToLong

data class UptimeJob(val url: String, val expectStatus: Int?, val intervalSec: Int)

data class GithubJob(
    val key: String,
    val repo: String,
    val branch: String?,
    val workflow: String?,
    val intervalSec: Int,
    val tokenSecret: String?
)

/**
 * What to poll for a config. Sources are coalesced: widgets that watch the same URL (or repo, branch and
 * workflow) share one job, polled at the shortest interval any of them asked for.
 */
data class SourcePlan(val uptime: Map<String, UptimeJob>, val github: Map<String, GithubJob>) {
    companion object {
        fun from(config: PanelConfig): SourcePlan {
            val uptime = linkedMapOf<String, UptimeJob>()
            val github = linkedMapOf<String, GithubJob>()
            for (widget in config.widgets) {
                when (widget.type) {
                    WidgetTypes.UPTIME -> WidgetParams.uptime(widget).value?.let { params ->
                        params.targets.forEach { target ->
                            val previous = uptime[target.url]
                            uptime[target.url] = UptimeJob(
                                url = target.url,
                                expectStatus = previous?.expectStatus ?: target.expectStatus,
                                intervalSec = min(previous?.intervalSec ?: Int.MAX_VALUE, params.intervalSec)
                            )
                        }
                    }

                    WidgetTypes.GITHUB_ACTIONS -> WidgetParams.githubActions(widget).value?.let { params ->
                        params.repos.forEach { repo ->
                            val key = WorkflowRunStatus.keyOf(repo, params.branch, params.workflow)
                            val previous = github[key]
                            github[key] = GithubJob(
                                key = key,
                                repo = repo,
                                branch = params.branch,
                                workflow = params.workflow,
                                intervalSec = min(previous?.intervalSec ?: Int.MAX_VALUE, params.intervalSec),
                                tokenSecret = previous?.tokenSecret ?: params.tokenSecret
                            )
                        }
                    }
                }
            }
            return SourcePlan(uptime, github)
        }

        /**
         * Delay before the next poll: the interval, doubled per consecutive failure up to [MAX_BACKOFF] times,
         * then spread by ±10% ([randomFraction] in 0..1) so many sources do not fire in lockstep.
         */
        fun nextDelayMillis(intervalSec: Int, consecutiveFailures: Int, randomFraction: Double): Long {
            val backoff = min(1L shl consecutiveFailures.coerceIn(0, MAX_BACKOFF_SHIFT), MAX_BACKOFF.toLong())
            val jitter = 0.9 + 0.2 * randomFraction.coerceIn(0.0, 1.0)
            return (intervalSec * 1_000L * backoff * jitter).roundToLong()
        }

        const val MAX_BACKOFF = 4
        private const val MAX_BACKOFF_SHIFT = 2
    }
}
