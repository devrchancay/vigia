package com.ramonchancay.vigia.feature.sources.github

import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** The fields of a workflow run the widget shows. Times are epoch millis. */
data class WorkflowRun(
    val name: String?,
    val branch: String?,
    val status: String?,
    val conclusion: String?,
    val htmlUrl: String?,
    val startedAtMillis: Long?,
    val updatedAtMillis: Long?
)

sealed interface RunsResponse {
    /** [run] is null when the repo has no runs for that branch/workflow. [etag] goes into the next request. */
    data class Fetched(val run: WorkflowRun?, val etag: String?) : RunsResponse

    /** 304: same as last time. Conditional requests that return 304 do not count against the rate limit. */
    data object NotModified : RunsResponse

    /** Rate limited until [resetAtMillis] (from x-ratelimit-reset / retry-after), when known. */
    data class RateLimited(val resetAtMillis: Long?) : RunsResponse

    data class Failed(val reason: String) : RunsResponse
}

/** Latest run of a repo through the GitHub REST API (`GET /repos/{repo}/actions/runs`). */
class GithubActionsClient @Inject constructor(
    private val client: OkHttpClient,
    @param:Named(BASE_URL_NAME) private val baseUrl: String,
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun latestRun(
        repo: String,
        branch: String?,
        workflow: String?,
        token: String?,
        etag: String?
    ): RunsResponse = withContext(ioDispatcher) {
        val path = if (workflow ==
            null
        ) {
            "repos/$repo/actions/runs"
        } else {
            "repos/$repo/actions/workflows/$workflow/runs"
        }
        val url = "$baseUrl/$path".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "1")
            .addQueryParameter("exclude_pull_requests", "true")
            .apply { branch?.let { addQueryParameter("branch", it) } }
            .build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .apply { etag?.let { header("If-None-Match", it) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> RunsResponse.NotModified

                    response.code == 429 ||
                        (response.code == 403 && response.header("x-ratelimit-remaining") == "0") ->
                        RunsResponse.RateLimited(
                            resetAt(response.header("x-ratelimit-reset"), response.header("retry-after"))
                        )

                    !response.isSuccessful -> RunsResponse.Failed("HTTP ${response.code}")

                    else -> {
                        val body = json.decodeFromString(RunsBody.serializer(), response.body.string())
                        RunsResponse.Fetched(body.workflowRuns.firstOrNull()?.toRun(), response.header("ETag"))
                    }
                }
            }
        } catch (e: IOException) {
            RunsResponse.Failed(e.javaClass.simpleName)
        } catch (e: SerializationException) {
            RunsResponse.Failed("Unexpected response")
        }
    }

    private fun resetAt(rateLimitReset: String?, retryAfter: String?): Long? =
        rateLimitReset?.toLongOrNull()?.let { it * MILLIS_PER_SECOND }
            ?: retryAfter?.toLongOrNull()?.let { System.currentTimeMillis() + it * MILLIS_PER_SECOND }

    @Serializable
    private data class RunsBody(@SerialName("workflow_runs") val workflowRuns: List<RunBody> = emptyList())

    @Serializable
    private data class RunBody(
        val name: String? = null,
        @SerialName("head_branch") val headBranch: String? = null,
        val status: String? = null,
        val conclusion: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("run_started_at") val runStartedAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    ) {
        fun toRun() =
            WorkflowRun(name, headBranch, status, conclusion, htmlUrl, runStartedAt.toMillis(), updatedAt.toMillis())
    }

    companion object {
        const val BASE_URL_NAME = "githubApiBaseUrl"
        const val DEFAULT_BASE_URL = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
        private const val MILLIS_PER_SECOND = 1_000L
        private val json = Json { ignoreUnknownKeys = true }

        private fun String?.toMillis(): Long? = this?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        }
    }
}
