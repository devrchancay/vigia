package com.ramonchancay.vigia.feature.sources.weather

import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import java.io.IOException
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

data class WeatherReading(
    val temperatureC: Double?,
    val apparentTemperatureC: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
    val maxC: Double?,
    val minC: Double?,
    val rainProbabilityMax: Int?
)

/** Current conditions and today's range from the Open-Meteo forecast API (free, no key, no sign-up). */
class WeatherClient @Inject constructor(
    private val client: OkHttpClient,
    @param:Named(BASE_URL_NAME) private val baseUrl: String,
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    /** The reading, or a short failure reason. */
    suspend fun current(latitude: Double, longitude: Double): Result<WeatherReading> = withContext(ioDispatcher) {
        val url = "$baseUrl/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", latitude.toString())
            .addQueryParameter("longitude", longitude.toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,weather_code,is_day")
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("forecast_days", "1")
            .addQueryParameter("timezone", "auto")
            .build()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("HTTP ${response.code}"))
                val body = json.decodeFromString(ForecastBody.serializer(), response.body.string())
                Result.success(
                    WeatherReading(
                        temperatureC = body.current?.temperature,
                        apparentTemperatureC = body.current?.apparentTemperature,
                        weatherCode = body.current?.weatherCode,
                        isDay = body.current?.isDay != 0,
                        maxC = body.daily?.max?.firstOrNull(),
                        minC = body.daily?.min?.firstOrNull(),
                        rainProbabilityMax = body.daily?.rainProbability?.firstOrNull()
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(IOException("Unexpected response", e))
        }
    }

    @Serializable
    private data class ForecastBody(val current: Current? = null, val daily: Daily? = null)

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature: Double? = null,
        @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
        @SerialName("weather_code") val weatherCode: Int? = null,
        @SerialName("is_day") val isDay: Int? = null
    )

    @Serializable
    private data class Daily(
        @SerialName("temperature_2m_max") val max: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val min: List<Double?> = emptyList(),
        @SerialName("precipitation_probability_max") val rainProbability: List<Int?> = emptyList()
    )

    companion object {
        const val BASE_URL_NAME = "openMeteoBaseUrl"
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
