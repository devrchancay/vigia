package com.ramonchancay.vigia.core.data.secrets

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Encrypted values by secret name, base64 of what [SecretCipher.encrypt] returns. */
@Serializable
data class SecretArchive(val values: Map<String, String> = emptyMap())

@Singleton
class EncryptedSecretStore @Inject constructor(
    private val dataStore: DataStore<SecretArchive>,
    private val cipher: SecretCipher,
    @param:Dispatcher(VigiaDispatchers.Default) private val cryptoDispatcher: CoroutineDispatcher
) : SecretStore {
    override suspend fun put(name: String, value: String) {
        require(value.length <= SecretStore.MAX_VALUE_LENGTH) {
            "Secret longer than ${SecretStore.MAX_VALUE_LENGTH} characters"
        }
        val sealed = withContext(cryptoDispatcher) { base64.encodeToString(cipher.encrypt(value.encodeToByteArray())) }
        dataStore.updateData { it.copy(values = it.values + (name to sealed)) }
    }

    override suspend fun delete(name: String): Boolean {
        var existed = false
        dataStore.updateData {
            existed = name in it.values
            it.copy(values = it.values - name)
        }
        return existed
    }

    override suspend fun get(name: String): String? {
        val sealed = dataStore.data.first().values[name] ?: return null
        return withContext(cryptoDispatcher) {
            // A key lost with an app reinstall or OS reset makes old values unreadable: treat as absent.
            runCatching { cipher.decrypt(base64Decoder.decode(sealed)).decodeToString() }.getOrNull()
        }
    }

    override suspend fun names(): List<String> = dataStore.data.first().values.keys.sorted()

    private companion object {
        val base64: Base64.Encoder = Base64.getEncoder()
        val base64Decoder: Base64.Decoder = Base64.getDecoder()
    }
}

internal object SecretArchiveSerializer : Serializer<SecretArchive> {
    private val json = Json { ignoreUnknownKeys = true }
    override val defaultValue = SecretArchive()

    override suspend fun readFrom(input: InputStream): SecretArchive = try {
        json.decodeFromString(SecretArchive.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable secrets file", e)
    }

    override suspend fun writeTo(t: SecretArchive, output: OutputStream) {
        output.write(json.encodeToString(SecretArchive.serializer(), t).encodeToByteArray())
    }
}
