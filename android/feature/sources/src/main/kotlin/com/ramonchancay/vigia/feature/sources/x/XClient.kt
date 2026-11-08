package com.ramonchancay.vigia.feature.sources.x

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

/** Follower count through the X API v2 user lookup (`GET /2/users/by/username/{username}`). */
class XClient @Inject constructor(
    private val client: OkHttpClient,
    @param:Named(BASE_URL_NAME) private val baseUrl: String,
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    /** The follower count, or a failure whose message is short enough for the tile (e.g. "HTTP 402"). */
    suspend fun followers(username: String, bearerToken: String): Result<Long> = withContext(ioDispatcher) {
        val url = "$baseUrl/2/users/by/username/$username".toHttpUrl().newBuilder()
            .addQueryParameter("user.fields", "public_metrics")
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $bearerToken").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("HTTP ${response.code}"))
                val body = json.decodeFromString(UserBody.serializer(), response.body.string())
                val count = body.data?.metrics?.followers
                    ?: return@withContext Result.failure(
                        IOException(body.errors.firstOrNull()?.title ?: "User not found")
                    )
                Result.success(count)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(IOException("Unexpected response", e))
        }
    }

    @Serializable
    private data class UserBody(val data: User? = null, val errors: List<ApiError> = emptyList())

    @Serializable
    private data class User(@SerialName("public_metrics") val metrics: Metrics? = null)

    @Serializable
    private data class Metrics(@SerialName("followers_count") val followers: Long? = null)

    @Serializable
    private data class ApiError(val title: String? = null)

    companion object {
        const val BASE_URL_NAME = "xApiBaseUrl"
        const val DEFAULT_BASE_URL = "https://api.x.com"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
