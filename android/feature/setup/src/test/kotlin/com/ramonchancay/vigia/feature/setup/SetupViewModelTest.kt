package com.ramonchancay.vigia.feature.setup

import app.cash.turbine.test
import com.ramonchancay.vigia.core.data.secrets.SecretStore
import com.ramonchancay.vigia.core.device.KioskController
import com.ramonchancay.vigia.core.device.KioskState
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
    private val kiosk = mockk<KioskController>(relaxUnitFun = true) {
        every { state() } returns KioskState(isDeviceOwner = true, isDefaultHome = true, isLockTaskActive = true)
    }
    private val secrets = mockk<SecretStore>().also { store ->
        coEvery { store.get(KioskController.PIN_SECRET) } returns
            "1234"
    }
    private val addresses = mockk<ServerAddresses> {
        coEvery { baseUrls() } returns listOf("http://100.64.0.1:8787", "http://192.168.1.5:8787")
    }

    @Test
    fun `shows the token and the reachable addresses`() = runTest {
        val viewModel = SetupViewModel(tokenStore, addresses, kiosk, secrets)

        // With the unconfined test dispatcher, Loading is replaced before the collector reads it.
        viewModel.uiState.test {
            val ready = awaitItem() as SetupUiState.Ready
            assertEquals("first-token", ready.token)
            assertEquals(listOf("http://100.64.0.1:8787", "http://192.168.1.5:8787"), ready.baseUrls)
            assertEquals(true, ready.pinRequired)
        }
    }

    @Test
    fun `rotating replaces the token on screen`() = runTest {
        val viewModel = SetupViewModel(tokenStore, addresses, kiosk, secrets)

        viewModel.uiState.test {
            assertEquals("first-token", (awaitItem() as SetupUiState.Ready).token)
            viewModel.onAction(SetupAction.RotateToken)
            assertEquals("second-token", (awaitItem() as SetupUiState.Ready).token)
        }
    }

    @Test
    fun `done marks setup completed and emits once`() = runTest {
        val viewModel = SetupViewModel(tokenStore, addresses, kiosk, secrets)

        viewModel.effects.test {
            viewModel.onAction(SetupAction.Done)
            assertEquals(SetupEvent.Done, awaitItem())
        }
        coVerify(exactly = 1) { tokenStore.markSetupCompleted() }
    }

    @Test
    fun `leaving kiosk needs the right pin and releasing clears the owner`() = runTest {
        val viewModel = SetupViewModel(tokenStore, addresses, kiosk, secrets)

        viewModel.effects.test {
            viewModel.onAction(SetupAction.ExitKiosk("0000"))
            expectNoEvents()
            viewModel.onAction(SetupAction.ExitKiosk("1234"))
            assertEquals(SetupEvent.StopLockTask, awaitItem())
            viewModel.onAction(SetupAction.ReleaseDeviceOwner("1234"))
            assertEquals(SetupEvent.StopLockTask, awaitItem())
        }
        io.mockk.verify(exactly = 1) { kiosk.releaseDeviceOwner() }
    }
}
