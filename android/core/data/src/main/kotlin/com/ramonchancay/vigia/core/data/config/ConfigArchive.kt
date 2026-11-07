package com.ramonchancay.vigia.core.data.config

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.ramonchancay.vigia.core.model.PanelConfig
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One accepted config with when and by whom it was saved. */
@Serializable
data class ConfigVersion(val config: PanelConfig, val savedAtEpochMillis: Long, val author: String)

/** What is persisted: the active config (null until the first change) and previous ones, newest first. */
@Serializable
data class ConfigArchive(val current: ConfigVersion? = null, val history: List<ConfigVersion> = emptyList()) {
    companion object {
        const val MAX_HISTORY = 20
    }
}

internal object ConfigArchiveSerializer : Serializer<ConfigArchive> {
    // Stored data was validated on write; tolerate fields added by newer app versions.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override val defaultValue = ConfigArchive()

    override suspend fun readFrom(input: InputStream): ConfigArchive = try {
        json.decodeFromString(ConfigArchive.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable config archive", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable config archive", e)
    }

    override suspend fun writeTo(t: ConfigArchive, output: OutputStream) {
        output.write(json.encodeToString(ConfigArchive.serializer(), t).encodeToByteArray())
    }
}
