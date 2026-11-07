package com.ramonchancay.vigia.core.data.config

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ramonchancay.vigia.core.model.GridConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BundledPanelConfigRepositoryTest {
    @Test
    fun `the bundled default config is valid and landscape`() = runTest {
        val repository = BundledPanelConfigRepository(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        repository.config.test {
            val config = awaitItem()
            assertEquals(GridConfig(columns = 8, rows = 4, gap = 8), config.grid)
            assertEquals(listOf("clock", "device", "note"), config.widgets.map { it.type })
            awaitComplete()
        }
    }
}
