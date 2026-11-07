package com.ramonchancay.vigia.core.data.secrets

import androidx.datastore.core.DataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedSecretStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Reversible stand-in for the Keystore cipher; enough to check nothing is stored in clear text. */
    private val xorCipher = object : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }
    private val file get() = File(folder.root, "secrets.json")

    private fun TestScope.store(cipher: SecretCipher = xorCipher) = EncryptedSecretStore(
        dataStore = DataStoreFactory.create(SecretArchiveSerializer, scope = backgroundScope, produceFile = { file }),
        cipher = cipher,
        cryptoDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    @Test
    fun `stores, lists and reads back a secret without keeping it in clear text`() = runTest {
        val store = store()

        store.put("github", "ghp_example_value")

        assertEquals("ghp_example_value", store.get("github"))
        assertEquals(listOf("github"), store.names())
        assertFalse(file.readText().contains("ghp_example_value"))
    }

    @Test
    fun `put replaces and delete reports whether it existed`() = runTest {
        val store = store()
        store.put("hook", "one")
        store.put("hook", "two")

        assertEquals("two", store.get("hook"))
        assertTrue(store.delete("hook"))
        assertFalse(store.delete("hook"))
        assertNull(store.get("hook"))
    }

    @Test
    fun `a value that no longer decrypts reads as absent`() = runTest {
        val store = store(
            cipher = object : SecretCipher {
                override fun encrypt(plain: ByteArray) = plain

                override fun decrypt(sealed: ByteArray): ByteArray = throw javax.crypto.AEADBadTagException()
            }
        )
        store.put("github", "value")

        assertNull(store.get("github"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects values over the size limit`() = runTest {
        store().put("big", "x".repeat(SecretStore.MAX_VALUE_LENGTH + 1))
    }
}
