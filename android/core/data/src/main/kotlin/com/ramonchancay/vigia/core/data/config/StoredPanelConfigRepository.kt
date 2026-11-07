package com.ramonchancay.vigia.core.data.config

import androidx.datastore.core.DataStore
import com.ramonchancay.vigia.core.model.ConfigError
import com.ramonchancay.vigia.core.model.ConfigParseResult
import com.ramonchancay.vigia.core.model.JsonMergePatch
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.PanelConfigParser
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The config the panel renders, persisted in DataStore with history. Until the first accepted change it serves
 * the bundled default. Every write emits on [config], so the panel updates live.
 */
@Singleton
class StoredPanelConfigRepository @Inject constructor(
    private val dataStore: DataStore<ConfigArchive>,
    private val defaultConfig: DefaultConfigSource,
    private val clock: Clock
) : PanelConfigRepository,
    PanelConfigEditor {

    override val config: Flow<PanelConfig> = dataStore.data.map { it.current?.config ?: defaultConfig.load() }

    override suspend fun current(): ConfigVersion = dataStore.data.first().current
        ?: ConfigVersion(defaultConfig.load(), savedAtEpochMillis = 0, author = DEFAULT_AUTHOR)

    override suspend fun replace(json: String, author: String): ConfigWriteResult =
        when (val parsed = PanelConfigParser.parse(json)) {
            is ConfigParseResult.Invalid -> ConfigWriteResult.Rejected(parsed.errors)
            is ConfigParseResult.Valid -> save(parsed.config, author)
        }

    override suspend fun patch(mergePatch: String, author: String): ConfigWriteResult {
        val patchElement = try {
            Json.parseToJsonElement(mergePatch)
        } catch (e: SerializationException) {
            return ConfigWriteResult.Rejected(listOf(malformed(e)))
        }
        val currentElement = PanelConfigParser.json.encodeToJsonElement(PanelConfig.serializer(), current().config)
        return replace(JsonMergePatch.apply(currentElement, patchElement).toString(), author)
    }

    override suspend fun validate(json: String, author: String): ConfigWriteResult =
        when (val parsed = PanelConfigParser.parse(json)) {
            is ConfigParseResult.Invalid -> ConfigWriteResult.Rejected(parsed.errors)

            is ConfigParseResult.Valid -> ConfigWriteResult.Accepted(
                ConfigVersion(parsed.config, clock.millis(), author)
            )
        }

    override suspend fun rollback(author: String): ConfigWriteResult {
        var restored: ConfigVersion? = null
        dataStore.updateData { archive ->
            val previous = archive.history.firstOrNull() ?: return@updateData archive
            restored = previous.copy(savedAtEpochMillis = clock.millis(), author = author)
            ConfigArchive(current = restored, history = archive.history.drop(1))
        }
        return restored?.let { ConfigWriteResult.Accepted(it) }
            ?: ConfigWriteResult.Rejected(
                listOf(ConfigError(ConfigError.Code.NothingToRollback, "", "There is no previous config to restore"))
            )
    }

    override suspend fun history(): List<ConfigVersion> = dataStore.data.first().history

    private suspend fun save(config: PanelConfig, author: String): ConfigWriteResult {
        val version = ConfigVersion(config, clock.millis(), author)
        dataStore.updateData { archive ->
            val previous = archive.current ?: ConfigVersion(defaultConfig.load(), 0, DEFAULT_AUTHOR)
            ConfigArchive(
                current = version,
                history = (listOf(previous) + archive.history).take(ConfigArchive.MAX_HISTORY)
            )
        }
        return ConfigWriteResult.Accepted(version)
    }

    private fun malformed(e: Exception) =
        ConfigError(ConfigError.Code.MalformedJson, "", e.message.orEmpty().lineSequence().firstOrNull().orEmpty())

    companion object {
        /** Author recorded for the bundled default config. */
        const val DEFAULT_AUTHOR = "default"
    }
}
