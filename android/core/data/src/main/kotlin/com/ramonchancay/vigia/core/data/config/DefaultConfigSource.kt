package com.ramonchancay.vigia.core.data.config

import android.content.Context
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import com.ramonchancay.vigia.core.model.ConfigParseResult
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.PanelConfigParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The config used until the first remote change, and after a reset. */
fun interface DefaultConfigSource {
    suspend fun load(): PanelConfig
}

/** Reads `assets/panel/default_config.json`; an invalid bundled document throws [InvalidPanelConfigException]. */
class BundledDefaultConfigSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : DefaultConfigSource {
    override suspend fun load(): PanelConfig = withContext(ioDispatcher) {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        when (val result = PanelConfigParser.parse(text)) {
            is ConfigParseResult.Valid -> result.config
            is ConfigParseResult.Invalid -> throw InvalidPanelConfigException(result.errors)
        }
    }

    companion object {
        const val ASSET_PATH = "panel/default_config.json"
    }
}
