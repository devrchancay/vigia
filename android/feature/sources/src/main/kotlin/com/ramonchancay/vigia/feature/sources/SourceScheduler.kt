package com.ramonchancay.vigia.feature.sources

import com.ramonchancay.vigia.core.common.task.AlwaysOnTask
import com.ramonchancay.vigia.core.data.config.PanelConfigRepository
import com.ramonchancay.vigia.core.data.secrets.SecretStore
import com.ramonchancay.vigia.core.data.status.StatusRepository
import com.ramonchancay.vigia.core.model.WeatherStatus
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import com.ramonchancay.vigia.feature.sources.github.GithubActionsClient
import com.ramonchancay.vigia.feature.sources.github.RunsResponse
import com.ramonchancay.vigia.feature.sources.uptime.UptimeChecker
import com.ramonchancay.vigia.feature.sources.weather.WeatherClient
import com.ramonchancay.vigia.feature.sources.x.XClient
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Polls every source the current config needs and records results in [StatusRepository]. Runs inside the
 * always-on service; a config change cancels all polls and starts the new plan, which checks at once.
 */
@Singleton
class SourceScheduler @Inject constructor(
    private val configRepository: PanelConfigRepository,
    private val status: StatusRepository,
    private val uptimeChecker: UptimeChecker,
    private val github: GithubActionsClient,
    private val secrets: SecretStore,
    private val weatherClient: WeatherClient,
    private val xClient: XClient,
    private val clock: Clock
) : AlwaysOnTask {
    override suspend fun run() {
        configRepository.config.map(SourcePlan::from).distinctUntilChanged().collectLatest { plan ->
            status.retainOnly(plan.uptime.keys, plan.github.keys, plan.weather.keys, plan.xFollowers.keys)
            Timber.i(
                "Polling %d uptime, %d GitHub and %d weather sources",
                plan.uptime.size,
                plan.github.size,
                plan.weather.size
            )
            coroutineScope {
                plan.uptime.values.forEach { launch { pollUptime(it) } }
                plan.github.values.forEach { launch { pollGithub(it) } }
                plan.weather.values.forEach { launch { pollWeather(it) } }
                plan.xFollowers.values.forEach { launch { pollXFollowers(it) } }
            }
        }
    }

    private suspend fun pollUptime(job: UptimeJob) {
        var failures = 0
        while (true) {
            val check = uptimeChecker.check(job.url, job.expectStatus)
            status.recordUptime(job.url, check.up, check.statusCode, check.latencyMs, check.error, clock.millis())
            // Back off only when no response arrived; a site that answers "down" is checked at the normal pace.
            failures = if (check.error != null) failures + 1 else 0
            delay(SourcePlan.nextDelayMillis(job.intervalSec, failures, Random.nextDouble()))
        }
    }

    private suspend fun pollGithub(job: GithubJob) {
        var failures = 0
        var etag: String? = null
        while (true) {
            val token = job.tokenSecret?.let { secrets.get(it) }
            if (job.tokenSecret != null && token == null) {
                record(job, error = "Secret '${job.tokenSecret}' is not set")
                failures++
            } else {
                when (val response = github.latestRun(job.repo, job.branch, job.workflow, token, etag)) {
                    is RunsResponse.Fetched -> {
                        etag = response.etag
                        failures = 0
                        val run = response.run
                        status.recordWorkflow(
                            WorkflowRunStatus(
                                key = job.key,
                                repo = job.repo,
                                runName = run?.name,
                                branch = run?.branch ?: job.branch,
                                status = run?.status,
                                conclusion = run?.conclusion,
                                htmlUrl = run?.htmlUrl,
                                startedAtMillis = run?.startedAtMillis,
                                updatedAtMillis = run?.updatedAtMillis,
                                checkedAtMillis = clock.millis()
                            )
                        )
                    }

                    RunsResponse.NotModified -> failures = 0

                    is RunsResponse.RateLimited -> {
                        record(job, error = "Rate limited by GitHub; add a token with tokenSecret")
                        val waitMillis = (response.resetAtMillis ?: 0L) - clock.millis()
                        if (waitMillis > 0) {
                            delay(waitMillis.coerceAtMost(MAX_RATE_LIMIT_WAIT_MILLIS))
                            continue
                        }
                        failures++
                    }

                    is RunsResponse.Failed -> {
                        record(job, error = response.reason)
                        failures++
                    }
                }
            }
            delay(SourcePlan.nextDelayMillis(job.intervalSec, failures, Random.nextDouble()))
        }
    }

    private suspend fun pollWeather(job: WeatherJob) {
        var failures = 0
        while (true) {
            val reading = weatherClient.current(job.latitude, job.longitude)
            val now = clock.millis()
            status.recordWeather(
                reading.fold(
                    onSuccess = {
                        WeatherStatus(
                            key = job.key,
                            temperatureC = it.temperatureC,
                            apparentTemperatureC = it.apparentTemperatureC,
                            weatherCode = it.weatherCode,
                            isDay = it.isDay,
                            maxC = it.maxC,
                            minC = it.minC,
                            rainProbabilityMax = it.rainProbabilityMax,
                            checkedAtMillis = now
                        )
                    },
                    onFailure = {
                        WeatherStatus(
                            key = job.key,
                            error = it.message ?: it.javaClass.simpleName,
                            checkedAtMillis = now
                        )
                    }
                )
            )
            failures = if (reading.isSuccess) 0 else failures + 1
            delay(SourcePlan.nextDelayMillis(job.intervalSec, failures, Random.nextDouble()))
        }
    }

    private suspend fun pollXFollowers(job: XFollowersJob) {
        var failures = 0
        while (true) {
            val token = secrets.get(job.tokenSecret)
            val result = if (token == null) {
                Result.failure(IllegalStateException("Secret '${job.tokenSecret}' is not set"))
            } else {
                xClient.followers(job.username, token)
            }
            result.fold(
                onSuccess = { status.recordMetric(job.key, it, clock.millis()) },
                onFailure = { status.recordMetricError(job.key, it.message ?: it.javaClass.simpleName, clock.millis()) }
            )
            failures = if (result.isSuccess) 0 else failures + 1
            delay(SourcePlan.nextDelayMillis(job.intervalSec, failures, Random.nextDouble()))
        }
    }

    /** Records an error while keeping the last known run, so the widget still shows it. */
    private suspend fun record(job: GithubJob, error: String) {
        val last = status.workflows.first()[job.key]
        status.recordWorkflow(
            (last ?: WorkflowRunStatus(key = job.key, repo = job.repo, branch = job.branch, checkedAtMillis = 0))
                .copy(error = error, checkedAtMillis = clock.millis())
        )
    }

    private companion object {
        const val MAX_RATE_LIMIT_WAIT_MILLIS = 60 * 60 * 1_000L
    }
}
