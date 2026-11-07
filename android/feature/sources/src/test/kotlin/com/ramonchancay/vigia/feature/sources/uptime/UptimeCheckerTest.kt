package com.ramonchancay.vigia.feature.sources.uptime

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UptimeCheckerTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun checker(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = UptimeChecker(client, dispatcher)

    @Test
    fun `2xx and 3xx are up, with latency`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        val check = checker(
            UnconfinedTestDispatcher(testScheduler)
        ).check(server.url("/").toString(), expectStatus = null)

        assertTrue(check.up)
        assertEquals(204, check.statusCode)
        assertNotNull(check.latencyMs)
        assertNull(check.error)
    }

    @Test
    fun `5xx is down and expectStatus overrides the default rule`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(200).build())
        val checker = checker(UnconfinedTestDispatcher(testScheduler))

        assertFalse(checker.check(server.url("/").toString(), expectStatus = null).up)
        val wrongStatus = checker.check(server.url("/").toString(), expectStatus = 401)
        assertFalse(wrongStatus.up)
        assertEquals(200, wrongStatus.statusCode)
    }

    @Test
    fun `no response is down with a reason`() = runTest {
        val url = server.url("/").toString()
        server.close()

        val check = checker(UnconfinedTestDispatcher(testScheduler)).check(url, expectStatus = null)

        assertFalse(check.up)
        assertNull(check.statusCode)
        assertNotNull(check.error)
    }
}
