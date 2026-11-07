package com.ramonchancay.vigia.feature.sources.github

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GithubActionsClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun kotlinx.coroutines.test.TestScope.client() = GithubActionsClient(
        OkHttpClient(),
        server.url("/").toString().trimEnd('/'),
        UnconfinedTestDispatcher(testScheduler)
    )

    @Test
    fun `parses the latest run and sends token, etag and filters`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("ETag", "\"abc\"").body(
                """{"total_count":1,"workflow_runs":[{"id":1,"name":"CI","head_branch":"main","status":"completed",
                "conclusion":"success","html_url":"https://github.com/owner/repo/actions/runs/1",
                "run_started_at":"2026-11-07T10:00:00Z","updated_at":"2026-11-07T10:03:30Z","extra":true}]}"""
            ).build()
        )

        val response = client().latestRun("owner/repo", "main", "ci.yml", token = "t0ken", etag = "\"old\"")

        val fetched = response as RunsResponse.Fetched
        assertEquals("\"abc\"", fetched.etag)
        assertEquals("success", fetched.run?.conclusion)
        val run = requireNotNull(fetched.run)
        assertEquals(210_000L, run.updatedAtMillis!! - run.startedAtMillis!!)
        val request = server.takeRequest()
        assertEquals("/repos/owner/repo/actions/workflows/ci.yml/runs", request.url.encodedPath)
        assertEquals("main", request.url.queryParameter("branch"))
        assertEquals("1", request.url.queryParameter("per_page"))
        assertEquals("Bearer t0ken", request.headers["Authorization"])
        assertEquals("\"old\"", request.headers["If-None-Match"])
    }

    @Test
    fun `no runs, not modified, rate limits and errors`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"workflow_runs":[]}""").build())
        server.enqueue(MockResponse.Builder().code(304).build())
        server.enqueue(
            MockResponse.Builder().code(
                403
            ).addHeader("x-ratelimit-remaining", "0").addHeader("x-ratelimit-reset", "1800000000").build()
        )
        server.enqueue(MockResponse.Builder().code(404).build())
        val client = client()

        assertNull((client.latestRun("o/r", null, null, null, null) as RunsResponse.Fetched).run)
        assertEquals(RunsResponse.NotModified, client.latestRun("o/r", null, null, null, "\"e\""))
        assertEquals(RunsResponse.RateLimited(1_800_000_000_000L), client.latestRun("o/r", null, null, null, null))
        assertEquals(RunsResponse.Failed("HTTP 404"), client.latestRun("o/r", null, null, null, null))
        assertEquals("/repos/o/r/actions/runs", server.takeRequest().url.encodedPath)
        assertNull(server.takeRequest().headers["Authorization"])
    }
}
