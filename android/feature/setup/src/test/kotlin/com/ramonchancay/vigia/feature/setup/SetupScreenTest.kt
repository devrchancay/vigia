package com.ramonchancay.vigia.feature.setup

import android.net.Uri
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// Landscape phone size, like the panel devices.
@Config(qualifiers = "en-w800dp-h400dp-land")
class SetupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ready = SetupUiState.Ready(token = "abc123", baseUrls = listOf("http://100.64.0.1:8787"))

    @Test
    fun showsAddressTokenAndQr() {
        composeRule.setContent { SetupScreen(uiState = ready, onAction = {}) }

        composeRule.onNodeWithText("http://100.64.0.1:8787").assertExists()
        composeRule.onNodeWithTag(SETUP_TOKEN_TAG).assertTextEquals("abc123")
        composeRule.onNodeWithTag(SETUP_QR_TAG).assertExists()
    }

    @Test
    fun rotationAsksForConfirmation() {
        val actions = mutableListOf<SetupAction>()
        composeRule.setContent { SetupScreen(uiState = ready, onAction = { actions += it }) }

        composeRule.onNodeWithText("Rotate token").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Rotate token").performClick()
        composeRule.onNodeWithText("Rotate").performClick()
        composeRule.onNodeWithText("Open panel").performClick()

        assertEquals(listOf(SetupAction.RotateToken, SetupAction.Done), actions)
    }

    @Test
    fun noNetworkHidesTheQr() {
        composeRule.setContent { SetupScreen(uiState = ready.copy(baseUrls = emptyList()), onAction = {}) }

        composeRule.onNodeWithText("No network. Connect the phone to Wi-Fi or Tailscale.").assertExists()
        composeRule.onNodeWithTag(SETUP_QR_TAG).assertDoesNotExist()
    }

    @Test
    fun pairingUriCarriesUrlAndToken() {
        val uri = Uri.parse(ready.pairingUri)

        assertEquals("vigia", uri.scheme)
        assertEquals("pair", uri.authority)
        assertEquals("http://100.64.0.1:8787", uri.getQueryParameter("url"))
        assertEquals("abc123", uri.getQueryParameter("token"))
    }

    @Test
    fun qrDecodesBackToThePairingUriWithARealToken() {
        val content = PairingQr.uri("http://100.101.102.103:8787", "U".repeat(43))
        val matrix = PairingQr.matrix(content)

        // Rebuild a scaled black-and-white image and read it with ZXing's own decoder.
        val scale = 4
        val side = matrix.size * scale
        val pixels = IntArray(side * side) { i ->
            val module = (i / side / scale) * matrix.size + (i % side / scale)
            if (matrix.dark[module]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))

        assertEquals(content, QRCodeReader().decode(bitmap).text)
    }
}
