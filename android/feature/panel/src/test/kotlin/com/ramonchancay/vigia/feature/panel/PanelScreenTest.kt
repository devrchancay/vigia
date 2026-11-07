package com.ramonchancay.vigia.feature.panel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetPosition
import com.ramonchancay.vigia.feature.panel.widget.CLOCK_TIME_TAG
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-land")
class PanelScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val config = PanelConfig(
        schemaVersion = 1,
        widgets = listOf(
            WidgetConfig("clock", "clock", WidgetPosition(0, 0, 5, 2)),
            WidgetConfig("radar", "hologram", WidgetPosition(5, 0, 3, 2)),
            WidgetConfig(
                "todo",
                "note",
                WidgetPosition(0, 2, 4, 2),
                title = "Today",
                params = JsonObject(mapOf("text" to JsonPrimitive("Ship M1")))
            ),
            WidgetConfig("empty", "note", WidgetPosition(4, 2, 4, 2))
        )
    )

    @Test
    fun rendersEachWidgetByType() {
        val now = mutableStateOf<Instant?>(Instant.parse("2026-11-06T21:42:07Z"))
        composeRule.setContent {
            PanelScreen(uiState = PanelUiState.Ready(config), now = now, onAction = {}, zone = ZoneOffset.ofHours(-5))
        }

        composeRule.onNodeWithTag(CLOCK_TIME_TAG).assertTextEquals("16:42:07")
        composeRule.onNodeWithText("Ship M1").assertExists()
        composeRule.onNodeWithText("Unsupported widget type: hologram").assertExists()
        composeRule.onNodeWithText("Missing parameter: params.text").assertExists()
    }

    @Test
    fun clockFollowsTicks() {
        val now = mutableStateOf<Instant?>(Instant.parse("2026-11-06T21:42:07Z"))
        composeRule.setContent {
            PanelScreen(uiState = PanelUiState.Ready(config), now = now, onAction = {}, zone = ZoneOffset.UTC)
        }

        now.value = Instant.parse("2026-11-06T21:42:08Z")

        composeRule.onNodeWithTag(CLOCK_TIME_TAG).assertTextEquals("21:42:08")
    }

    @Test
    fun emptyConfigShowsAHint() {
        val now = mutableStateOf<Instant?>(null)
        composeRule.setContent {
            PanelScreen(uiState = PanelUiState.Ready(PanelConfig(schemaVersion = 1)), now = now, onAction = {})
        }

        composeRule.onNodeWithText("No widgets configured yet.").assertExists()
    }

    @Test
    fun errorStateSendsRetry() {
        val actions = mutableListOf<PanelAction>()
        val now = mutableStateOf<Instant?>(null)
        composeRule.setContent {
            PanelScreen(uiState = PanelUiState.Error, now = now, onAction = { actions += it })
        }

        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf(PanelAction.Retry), actions)
    }
}
