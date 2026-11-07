package com.ramonchancay.vigia.feature.setup

import app.cash.turbine.test
import com.ramonchancay.vigia.core.server.ServerAddresses
import com.ramonchancay.vigia.core.server.security.ServerTokenStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tokenFlow = MutableStateFlow("first-token")
    private val tokenStore = mockk<ServerTokenStore>(relaxUnitFun = true) {
        every { token } returns tokenFlow
        coEvery { currentToken() } returns "first-token"
        coEvery { rotate() } coAnswers { "second-token".also { tokenFlow.value = it } }
    }
    private val addresses = mockk<ServerAddresses> {
        coEvery { baseUrls() } returns listOf("http://100.64.0.1:8787", "http://192.168.1.5:8787")
    }

    @Test
    fun `shows the token and the reachable addresses`() = runTest {
        val viewModel = SetupViewModel(tokenStore, addresses)

        // With the unconfined test dispatcher, Loading is replaced before the collector reads it.
        viewModel.uiState.test {
            assertEquals(
                SetupUiState.Ready("first-token", listOf("http://100.64.0.1:8787", "http://192.168.1.5:8787")),
                awaitItem()
            )
        }
    }

    @Test
    fun `rotating replaces the token on screen`() = runTest {
        val viewModel = SetupViewModel(tokenStore, addresses)

        viewModel.uiState.test {
            assertEquals("first-token", (awaitItem() as SetupUiState.Ready).token)
            viewModel.onAction(SetupAction.RotateToken)
            assertEquals("second-token", (awaitItem() as SetupUiState.Ready).token)
        }
    }

    @Test
    fun `done marks setup completed and emits once`() = runTest {
        val viewModel = SetupViewModel(tokenStore, addresses)

        viewModel.done.test {
            viewModel.onAction(SetupAction.Done)
            awaitItem()
        }
        coVerify(exactly = 1) { tokenStore.markSetupCompleted() }
    }
}
