package com.ramonchancay.vigia.feature.panel

import app.cash.turbine.test
import com.ramonchancay.vigia.core.data.config.PanelConfigRepository
import com.ramonchancay.vigia.core.model.GridConfig
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.feature.panel.data.ClockRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PanelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val configRepository = mockk<PanelConfigRepository>()
    private val clockRepository = mockk<ClockRepository> { every { ticks() } returns emptyFlow() }

    @Test
    fun `starts loading and shows each config`() = runTest {
        val configs = MutableSharedFlow<PanelConfig>()
        every { configRepository.config } returns configs
        val viewModel = PanelViewModel(configRepository, clockRepository)

        viewModel.uiState.test {
            assertEquals(PanelUiState.Loading, awaitItem())
            configs.emit(CONFIG)
            assertEquals(PanelUiState.Ready(CONFIG), awaitItem())
            configs.emit(OTHER_CONFIG)
            assertEquals(PanelUiState.Ready(OTHER_CONFIG), awaitItem())
        }
    }

    @Test
    fun `shows error when the config fails and recovers on retry`() = runTest {
        val failNow = MutableSharedFlow<Unit>()
        val configsAfterRetry = MutableSharedFlow<PanelConfig>()
        every { configRepository.config } returnsMany listOf(
            flow {
                failNow.first()
                throw IllegalStateException("broken config")
            },
            configsAfterRetry
        )
        val viewModel = PanelViewModel(configRepository, clockRepository)

        viewModel.uiState.test {
            assertEquals(PanelUiState.Loading, awaitItem())
            failNow.emit(Unit)
            assertEquals(PanelUiState.Error, awaitItem())

            viewModel.onAction(PanelAction.Retry)

            assertEquals(PanelUiState.Loading, awaitItem())
            configsAfterRetry.emit(CONFIG)
            assertEquals(PanelUiState.Ready(CONFIG), awaitItem())
        }
        verify(exactly = 2) { configRepository.config }
    }

    @Test
    fun `exposes clock ticks separately from the layout`() = runTest {
        val ticks = MutableSharedFlow<Instant>()
        every { configRepository.config } returns emptyFlow()
        every { clockRepository.ticks() } returns ticks
        val viewModel = PanelViewModel(configRepository, clockRepository)

        viewModel.now.test {
            assertNull(awaitItem())
            ticks.emit(FIRST)
            assertEquals(FIRST, awaitItem())
        }
    }

    @Test
    fun `a failing clock does not crash and keeps the last tick`() = runTest {
        every { configRepository.config } returns emptyFlow()
        every { clockRepository.ticks() } returns flow {
            emit(FIRST)
            throw IllegalStateException("clock broke")
        }
        val viewModel = PanelViewModel(configRepository, clockRepository)

        viewModel.now.test {
            assertEquals(FIRST, awaitItem())
            expectNoEvents()
        }
    }

    private companion object {
        val CONFIG = PanelConfig(schemaVersion = 1)
        val OTHER_CONFIG = PanelConfig(schemaVersion = 1, grid = GridConfig(columns = 4, rows = 2))
        val FIRST: Instant = Instant.parse("2026-11-06T21:42:07Z")
    }
}
