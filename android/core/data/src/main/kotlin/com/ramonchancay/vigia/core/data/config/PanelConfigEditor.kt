package com.ramonchancay.vigia.core.data.config

import com.ramonchancay.vigia.core.model.ConfigError

sealed interface ConfigWriteResult {
    /** For a dry run ([PanelConfigEditor.validate]) nothing is stored and [version] is what would be saved. */
    data class Accepted(val version: ConfigVersion) : ConfigWriteResult

    data class Rejected(val errors: List<ConfigError>) : ConfigWriteResult
}

/** Remote control of the config (milestone M2 HTTP API). Every write is validated; invalid documents are never stored. */
interface PanelConfigEditor {
    suspend fun current(): ConfigVersion

    /** Replaces the whole config with [json]. */
    suspend fun replace(json: String, author: String): ConfigWriteResult

    /** Applies a JSON Merge Patch (RFC 7396) to the current config. */
    suspend fun patch(mergePatch: String, author: String): ConfigWriteResult

    /** Parses and validates [json] without storing it. */
    suspend fun validate(json: String, author: String): ConfigWriteResult

    /** Restores the previous version and discards the current one (undo). Rejected when there is no history. */
    suspend fun rollback(author: String): ConfigWriteResult

    /** Previous versions, newest first, at most [ConfigArchive.MAX_HISTORY]. */
    suspend fun history(): List<ConfigVersion>
}
