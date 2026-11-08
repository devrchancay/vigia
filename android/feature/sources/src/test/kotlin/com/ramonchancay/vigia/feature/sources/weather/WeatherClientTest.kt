package com.ramonchancay.vigia.feature.sources.weather

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `parses current conditions and today's range`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"latitude":-2.2,"current":{"time":"2026-11-07T19:00","temperature_2m":27.4,"apparent_temperature":30.1,
                "weather_code":3,"is_day":0},"daily":{"time":["2026-11-07"],"temperature_2m_max":[31.2],
                "temperature_2m_min":[22.8],"precipitation_probability_max":[40]}}"""
            ).build()
        )
        val client =
            WeatherClient(
                OkHttpClient(),
                server.url("/").toString().trimEnd('/'),
                UnconfinedTestDispatcher(testScheduler)
            )

        val reading = client.current(-2.19, -79.89).getOrThrow()

        assertEquals(
            WeatherReading(27.4, 30.1, 3, isDay = false, maxC = 31.2, minC = 22.8, rainProbabilityMax = 40),
            reading
        )
        val request = server.takeRequest()
        assertEquals("/v1/forecast", request.url.encodedPath)
        assertEquals("-2.19", request.url.queryParameter("latitude"))
        assertEquals("auto", request.url.queryParameter("timezone"))
    }

    @Test
    fun `http errors are failures`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).build())
        val client =
            WeatherClient(
                OkHttpClient(),
                server.url("/").toString().trimEnd('/'),
                UnconfinedTestDispatcher(testScheduler)
            )

        assertTrue(client.current(0.0, 0.0).isFailure)
    }
}
