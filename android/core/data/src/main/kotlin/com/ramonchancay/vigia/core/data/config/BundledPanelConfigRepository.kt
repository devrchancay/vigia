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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Serves the default config shipped in the APK (`assets/panel/default_config.json`).
 * Milestone M2 replaces it with a DataStore-backed repository that accepts remote updates.
 */
class BundledPanelConfigRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : PanelConfigRepository {
    override val config: Flow<PanelConfig> = flow {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        when (val result = PanelConfigParser.parse(text)) {
            is ConfigParseResult.Valid -> emit(result.config)
            is ConfigParseResult.Invalid -> throw InvalidPanelConfigException(result.errors)
        }
    }.flowOn(ioDispatcher)

    companion object {
        const val ASSET_PATH = "panel/default_config.json"
    }
}
