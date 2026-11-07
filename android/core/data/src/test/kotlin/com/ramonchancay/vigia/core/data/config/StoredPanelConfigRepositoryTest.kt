package com.ramonchancay.vigia.core.data.config

import androidx.datastore.core.DataStoreFactory
import app.cash.turbine.test
import com.ramonchancay.vigia.core.model.ConfigError
import com.ramonchancay.vigia.core.model.GridConfig
import com.ramonchancay.vigia.core.model.PanelConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoredPanelConfigRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val default = PanelConfig(schemaVersion = 1)
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    private fun TestScope.repository() = StoredPanelConfigRepository(
        dataStore = DataStoreFactory.create(
            serializer = ConfigArchiveSerializer,
            scope = backgroundScope,
            produceFile = { folder.newFile("archive.json").also { it.delete() } }
        ),
        defaultConfig = { default },
        clock = clock
    )

    private fun grid(columns: Int) = """{"schemaVersion":1,"grid":{"columns":$columns,"rows":4}}"""

    @Test
    fun `serves the default config until the first change`() = runTest {
        val repository = repository()

        assertEquals(default, repository.current().config)
        assertEquals(StoredPanelConfigRepository.DEFAULT_AUTHOR, repository.current().author)
        assertTrue(repository.history().isEmpty())
    }

    @Test
    fun `replace stores the config, records author and time, and emits it`() = runTest {
        val repository = repository()

        repository.config.test {
            assertEquals(default, awaitItem())
            val result = repository.replace(grid(6), author = "cli")
            assertEquals(GridConfig(columns = 6, rows = 4), awaitItem().grid)
            val version = (result as ConfigWriteResult.Accepted).version
            assertEquals("cli", version.author)
            assertEquals(1_000L, version.savedAtEpochMillis)
        }
        assertEquals(listOf(default), repository.history().map { it.config })
    }

    @Test
    fun `invalid configs are rejected and never stored`() = runTest {
        val repository = repository()

        val result = repository.replace(grid(0), author = "cli")

        assertEquals("grid.columns", (result as ConfigWriteResult.Rejected).errors.single().path)
        assertEquals(default, repository.current().config)
        assertTrue(repository.history().isEmpty())
    }

    @Test
    fun `patch merges into the current config`() = runTest {
        val repository = repository()
        repository.replace(grid(6), author = "cli")

        val result = repository.patch("""{"theme":{"palette":"teal"}}""", author = "agent")

        val config = (result as ConfigWriteResult.Accepted).version.config
        assertEquals("teal", config.theme.palette)
        assertEquals(6, config.grid.columns)
    }

    @Test
    fun `a patch that breaks the config is rejected`() = runTest {
        val repository = repository()

        val result = repository.patch("""{"grid":{"columns":99}}""", author = "agent")

        assertEquals("grid.columns", (result as ConfigWriteResult.Rejected).errors.single().path)
    }

    @Test
    fun `malformed patch json is rejected`() = runTest {
        val result = repository().patch("{not json", author = "agent")

        assertEquals(ConfigError.Code.MalformedJson, (result as ConfigWriteResult.Rejected).errors.single().code)
    }

    @Test
    fun `validate does not store`() = runTest {
        val repository = repository()

        val result = repository.validate(grid(6), author = "cli")

        assertTrue(result is ConfigWriteResult.Accepted)
        assertEquals(default, repository.current().config)
    }

    @Test
    fun `rollback restores the previous version and discards the current one`() = runTest {
        val repository = repository()
        repository.replace(grid(6), author = "cli")
        repository.replace(grid(5), author = "cli")

        val first = repository.rollback(author = "cli")
        assertEquals(6, (first as ConfigWriteResult.Accepted).version.config.grid.columns)
        val second = repository.rollback(author = "cli")
        assertEquals(default, (second as ConfigWriteResult.Accepted).version.config)

        val third = repository.rollback(author = "cli")
        assertEquals(ConfigError.Code.NothingToRollback, (third as ConfigWriteResult.Rejected).errors.single().code)
    }

    @Test
    fun `history keeps the last 20 versions, newest first`() = runTest {
        val repository = repository()
        (1..24).forEach { repository.replace(grid((it % 24) + 1), author = "v$it") }

        val history = repository.history()

        assertEquals(ConfigArchive.MAX_HISTORY, history.size)
        assertEquals("v23", history.first().author)
    }
}
