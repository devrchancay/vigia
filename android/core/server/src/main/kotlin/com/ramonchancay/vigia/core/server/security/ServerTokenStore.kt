package com.ramonchancay.vigia.core.server.security

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class ServerSettings(val token: String = "")

/** The API bearer token: generated on first use, stored in app-private storage, rotatable from the setup screen. */
@Singleton
class ServerTokenStore @Inject constructor(private val dataStore: DataStore<ServerSettings>) {
    /** Current token; creates one the first time. */
    val token: Flow<String> = dataStore.data.map { it.token }

    suspend fun currentToken(): String {
        val existing = dataStore.data.first().token
        if (existing.isNotEmpty()) return existing
        return dataStore.updateData { if (it.token.isEmpty()) it.copy(token = newToken()) else it }.token
    }

    /** Replaces the token; clients using the old one get 401 from then on. */
    suspend fun rotate(): String = dataStore.updateData { it.copy(token = newToken()) }.token

    /** Constant-time comparison so response timing does not leak how much of a guess was right. */
    suspend fun matches(candidate: String): Boolean {
        val token = currentToken()
        return MessageDigest.isEqual(token.encodeToByteArray(), candidate.encodeToByteArray())
    }

    companion object {
        private const val TOKEN_BYTES = 32
        private val random = SecureRandom()

        /** 256 random bits, URL-safe base64 without padding (43 chars). */
        fun newToken(): String {
            val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

internal object ServerSettingsSerializer : Serializer<ServerSettings> {
    private val json = Json { ignoreUnknownKeys = true }
    override val defaultValue = ServerSettings()

    override suspend fun readFrom(input: InputStream): ServerSettings = try {
        json.decodeFromString(ServerSettings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable server settings", e)
    }

    override suspend fun writeTo(t: ServerSettings, output: OutputStream) {
        output.write(json.encodeToString(ServerSettings.serializer(), t).encodeToByteArray())
    }
}
