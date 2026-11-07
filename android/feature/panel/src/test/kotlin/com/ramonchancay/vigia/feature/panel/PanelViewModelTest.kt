package com.ramonchancay.vigia.feature.panel

import app.cash.turbine.test
import com.ramonchancay.vigia.feature.panel.data.ClockRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PanelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clockRepository = mockk<ClockRepository>()

    @Test
    fun `starts loading and shows each tick`() = runTest {
        val ticks = MutableSharedFlow<Instant>()
        every { clockRepository.ticks() } returns ticks
        val viewModel = PanelViewModel(clockRepository)

        viewModel.uiState.test {
            assertEquals(PanelUiState.Loading, awaitItem())
            ticks.emit(FIRST)
            assertEquals(PanelUiState.Ready(FIRST), awaitItem())
            ticks.emit(SECOND)
            assertEquals(PanelUiState.Ready(SECOND), awaitItem())
        }
    }

    @Test
    fun `shows error when the clock fails and recovers on retry`() = runTest {
        val failNow = MutableSharedFlow<Unit>()
        val ticksAfterRetry = MutableSharedFlow<Instant>()
        every { clockRepository.ticks() } returnsMany listOf(
            flow {
                failNow.first()
                throw IllegalStateException("clock broke")
            },
            ticksAfterRetry
        )
        val viewModel = PanelViewModel(clockRepository)

        viewModel.uiState.test {
            assertEquals(PanelUiState.Loading, awaitItem())
            failNow.emit(Unit)
            assertEquals(PanelUiState.Error, awaitItem())

            viewModel.onAction(PanelAction.Retry)

            assertEquals(PanelUiState.Loading, awaitItem())
            ticksAfterRetry.emit(FIRST)
            assertEquals(PanelUiState.Ready(FIRST), awaitItem())
        }
        verify(exactly = 2) { clockRepository.ticks() }
    }

    private companion object {
        val FIRST: Instant = Instant.parse("2026-11-06T21:42:07Z")
        val SECOND: Instant = Instant.parse("2026-11-06T21:42:08Z")
    }
}
