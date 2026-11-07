package com.ramonchancay.vigia.feature.panel

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ramonchancay.vigia.core.designsystem.theme.VigiaTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class PanelScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyStateShowsTheTimeInTheGivenZone() {
        composeRule.setContent {
            VigiaTheme {
                PanelScreen(
                    uiState = PanelUiState.Ready(Instant.parse("2026-11-06T21:42:07Z")),
                    onAction = {},
                    zone = ZoneOffset.ofHours(-5)
                )
            }
        }

        composeRule.onNodeWithTag(PANEL_TIME_TAG).assertTextEquals("16:42:07")
        composeRule.onNodeWithText("No widgets configured yet.").assertExists()
    }

    @Test
    fun errorStateSendsRetry() {
        val actions = mutableListOf<PanelAction>()
        composeRule.setContent {
            VigiaTheme {
                PanelScreen(uiState = PanelUiState.Error, onAction = { actions += it })
            }
        }

        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf(PanelAction.Retry), actions)
    }
}
