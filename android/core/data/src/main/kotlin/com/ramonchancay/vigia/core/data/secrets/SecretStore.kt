package com.ramonchancay.vigia.core.data.secrets

/**
 * Write-only secrets for sources (GitHub token, webhook URLs), referenced from the config by name and never
 * returned by the API. Values are encrypted at rest with a key that never leaves the Android Keystore.
 */
interface SecretStore {
    suspend fun put(name: String, value: String)

    /** True when a secret with that name existed. */
    suspend fun delete(name: String): Boolean

    /** For on-device sources only; never expose it through the API or logs. */
    suspend fun get(name: String): String?

    suspend fun names(): List<String>

    companion object {
        const val MAX_VALUE_LENGTH = 4_096
    }
}
