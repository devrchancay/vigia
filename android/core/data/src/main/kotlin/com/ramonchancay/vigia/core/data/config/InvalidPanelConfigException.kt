package com.ramonchancay.vigia.core.data.config

import com.ramonchancay.vigia.core.model.ConfigError

class InvalidPanelConfigException(val errors: List<ConfigError>) :
    IllegalStateException(
        "Invalid panel config: " + errors.joinToString("; ") { "${it.path.ifEmpty { "<root>" }}: ${it.message}" }
    )
