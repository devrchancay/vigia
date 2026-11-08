package com.ramonchancay.vigia.feature.sources.x

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun TestScope.client() =
        XClient(OkHttpClient(), server.url("/").toString().trimEnd('/'), UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `reads followers_count with the bearer token`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"data":{"id":"1","name":"Someone","username":"someone","public_metrics":{"followers_count":1234,"following_count":5}}}"""
            ).build()
        )

        assertEquals(1234L, client().followers("someone", "t0ken").getOrThrow())
        val request = server.takeRequest()
        assertEquals("/2/users/by/username/someone", request.url.encodedPath)
        assertEquals("public_metrics", request.url.queryParameter("user.fields"))
        assertEquals("Bearer t0ken", request.headers["Authorization"])
    }

    @Test
    fun `payment required, unknown users and errors are short failures`() = runTest {
        server.enqueue(MockResponse.Builder().code(402).build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"errors":[{"title":"Not Found Error"}]}""").build())
        val client = client()

        assertEquals("HTTP 402", client.followers("a", "t").exceptionOrNull()?.message)
        assertEquals("Not Found Error", client.followers("b", "t").exceptionOrNull()?.message)
    }
}
