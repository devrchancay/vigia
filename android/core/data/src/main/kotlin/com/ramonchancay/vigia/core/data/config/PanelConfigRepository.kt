package com.ramonchancay.vigia.core.data.config

import com.ramonchancay.vigia.core.model.PanelConfig
import kotlinx.coroutines.flow.Flow

/** Source of the panel config the screen renders. */
interface PanelConfigRepository {
    /**
     * Emits the current config and, once remote updates exist (milestone M2), every accepted change.
     * Fails with [InvalidPanelConfigException] if the stored document does not validate.
     */
    val config: Flow<PanelConfig>
}
