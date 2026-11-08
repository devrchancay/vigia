package com.ramonchancay.vigia.feature.panel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ramonchancay.vigia.core.device.BatteryStatus
import com.ramonchancay.vigia.core.device.DeviceStatus
import com.ramonchancay.vigia.core.device.MemoryStatus
import com.ramonchancay.vigia.core.device.WifiStatus
import com.ramonchancay.vigia.core.model.ConfigParseResult
import com.ramonchancay.vigia.core.model.EventLevel
import com.ramonchancay.vigia.core.model.MetricStatus
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.PanelConfigParser
import com.ramonchancay.vigia.core.model.StoredEvent
import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.WeatherStatus
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetPosition
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import com.ramonchancay.vigia.feature.panel.widget.AGENT_ROW_TAG
import com.ramonchancay.vigia.feature.panel.widget.CLOCK_TIME_TAG
import com.ramonchancay.vigia.feature.panel.widget.DEVICE_ROW_TAG
import com.ramonchancay.vigia.feature.panel.widget.METRIC_VALUE_TAG
import com.ramonchancay.vigia.feature.panel.widget.WEATHER_TEMP_TAG
import com.ramonchancay.vigia.feature.panel.widget.WEB_TAG
import com.ramonchancay.vigia.feature.panel.widget.WidgetData
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// Landscape phone size: one row of the default 8x4 grid is about 90 dp, two rows about 190 dp.
@Config(qualifiers = "en-w800dp-h400dp-land")
class PanelScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun data(
        now: Instant? = null,
        device: DeviceStatus? = null,
        uptime: Map<String, UptimeStatus> = emptyMap(),
        workflows: Map<String, WorkflowRunStatus> = emptyMap(),
        events: List<StoredEvent> = emptyList(),
        weather: Map<String, WeatherStatus> = emptyMap(),
        metrics: Map<String, MetricStatus> = emptyMap(),
        zone: ZoneId = ZoneOffset.UTC
    ) = WidgetData(
        mutableStateOf(now),
        mutableStateOf(device),
        mutableStateOf(uptime),
        mutableStateOf(workflows),
        mutableStateOf(events),
        mutableStateOf(weather),
        mutableStateOf(metrics),
        zone
    )

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
        val data = data(now = Instant.parse("2026-11-06T21:42:07Z"), zone = ZoneOffset.ofHours(-5))
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        composeRule.onNodeWithTag(CLOCK_TIME_TAG).assertTextEquals("16:42:07")
        composeRule.onNodeWithText("Ship M1").assertExists()
        composeRule.onNodeWithText("Unsupported widget type: hologram").assertExists()
        composeRule.onNodeWithText("Invalid parameter: params").assertExists()
    }

    @Test
    fun clockFollowsTicks() {
        val now = mutableStateOf<Instant?>(Instant.parse("2026-11-06T21:42:07Z"))
        val data =
            WidgetData(
                now,
                mutableStateOf(null),
                mutableStateOf(emptyMap()),
                mutableStateOf(emptyMap()),
                mutableStateOf(emptyList()),
                mutableStateOf(emptyMap()),
                mutableStateOf(emptyMap()),
                ZoneOffset.UTC
            )
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        now.value = Instant.parse("2026-11-06T21:42:08Z")

        composeRule.onNodeWithTag(CLOCK_TIME_TAG).assertTextEquals("21:42:08")
    }

    @Test
    fun deviceWidgetShowsTheRequestedRows() {
        val device = DeviceStatus(
            battery = BatteryStatus(levelPercent = 80, temperatureCelsius = 41f, isCharging = true),
            memory = MemoryStatus(availableBytes = 1L shl 30, totalBytes = 3L shl 30),
            wifi = WifiStatus(isConnected = true, signalLevel = 3)
        )
        val config = PanelConfig(
            schemaVersion = 1,
            widgets = listOf(
                WidgetConfig(
                    "device",
                    "device",
                    WidgetPosition(0, 0, 4, 4),
                    params = JsonObject(
                        mapOf("show" to JsonArray(listOf("battery", "memory", "wifi").map(::JsonPrimitive)))
                    )
                ),
                WidgetConfig(
                    "bad",
                    "device",
                    WidgetPosition(4, 0, 4, 4),
                    params = JsonObject(mapOf("show" to JsonArray(listOf(JsonPrimitive("gpu")))))
                )
            )
        )
        val data = data(device = device)
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        composeRule.onNodeWithText("80% · charging").assertExists()
        composeRule.onNodeWithText("2.0 / 3.0 GB").assertExists()
        composeRule.onNodeWithText("●●●○").assertExists()
        composeRule.onNodeWithTag(DEVICE_ROW_TAG + "batteryTemp").assertDoesNotExist()
        composeRule.onNodeWithText("Invalid parameter: params.show[0]").assertExists()
    }

    @Test
    fun uptimeAndGithubWidgetsShowLiveStatus() {
        val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli()
        val config = (
            PanelConfigParser.parse(
                """
                {"schemaVersion":1,"widgets":[
                  {"id":"sites","type":"uptime","pos":{"x":0,"y":0,"w":4,"h":4},"params":{"targets":[
                    {"name":"site","url":"https://example.com"},
                    {"name":"api","url":"https://api.example.com"},
                    {"name":"new","url":"https://new.example.com"}]}},
                  {"id":"ci","type":"github_actions","pos":{"x":4,"y":0,"w":4,"h":4},"params":{"repos":["owner/repo","owner/broken"],"branch":"main"}}
                ]}
                """.trimIndent()
            ) as ConfigParseResult.Valid
            ).config
        val uptime = mapOf(
            "https://example.com" to UptimeStatus(
                "https://example.com",
                true,
                200,
                120,
                null,
                now,
                now - 3 * 3_600_000
            ),
            "https://api.example.com" to
                UptimeStatus("https://api.example.com", false, 503, 40, null, now, now - 5 * 60_000)
        )
        val workflows = mapOf(
            "owner/repo|main|" to WorkflowRunStatus(
                key = "owner/repo|main|",
                repo = "owner/repo",
                runName = "CI",
                status = "completed",
                conclusion = "success",
                startedAtMillis = now - 237_000,
                updatedAtMillis = now,
                checkedAtMillis = now
            ),
            "owner/broken|main|" to WorkflowRunStatus(
                key = "owner/broken|main|",
                repo = "owner/broken",
                error = "HTTP 404",
                checkedAtMillis = now
            )
        )
        val data = data(now = Instant.ofEpochMilli(now), uptime = uptime, workflows = workflows)
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        composeRule.onNodeWithText("120 ms").assertExists()
        composeRule.onNodeWithText("3h").assertExists()
        composeRule.onNodeWithText("HTTP 503").assertExists()
        composeRule.onNodeWithText("5m").assertExists()
        composeRule.onNodeWithText("checking…").assertExists()
        composeRule.onNodeWithText("3m 57s").assertExists()
        composeRule.onNodeWithText("✓").assertExists()
        composeRule.onNodeWithText("HTTP 404").assertExists()
    }

    @Test
    fun oneRowTilesUseTheCompactLayouts() {
        val config = PanelConfig(
            schemaVersion = 1,
            widgets = listOf(
                WidgetConfig("clock", "clock", WidgetPosition(0, 0, 3, 1)),
                WidgetConfig("device", "device", WidgetPosition(3, 0, 5, 1))
            )
        )
        val device = DeviceStatus(
            battery = BatteryStatus(levelPercent = 92, temperatureCelsius = 34f, isCharging = true),
            memory = MemoryStatus(availableBytes = 1L shl 30, totalBytes = 2L shl 30),
            wifi = WifiStatus(isConnected = true, signalLevel = 4)
        )
        val data = data(now = Instant.parse("2026-11-07T18:45:51Z"), device = device)
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        composeRule.onNodeWithTag(CLOCK_TIME_TAG).assertTextEquals("18:45")
        composeRule.onNodeWithText("Sat 7 Nov").assertExists()
        composeRule.onNodeWithText("92% ⚡").assertExists()
        composeRule.onNodeWithText("1.0/2.0 GB").assertExists()
    }

    @Test
    fun agentsPutSessionsWaitingForPermissionFirstAndFeedMixesSources() {
        val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli()
        fun event(
            id: String,
            source: String,
            title: String,
            kind: String? = null,
            minutesAgo: Long = 1,
            expired: Boolean = false
        ) = StoredEvent(
            id = id,
            source = source,
            key = id,
            kind = kind,
            title = title,
            level = EventLevel.Warn,
            receivedAtMillis = now - minutesAgo * 60_000,
            expiresAtMillis = if (expired) now - 1 else now + 3_600_000
        )
        val events = listOf(
            event("s1", "claude-code", "vigia: building", kind = "working", minutesAgo = 2),
            event("s2", "claude-code", "pet-meet: needs permission", kind = "waiting_permission", minutesAgo = 10),
            event("s3", "claude-code", "parejo: done", kind = "done", minutesAgo = 30),
            event("s4", "claude-code", "old session", kind = "working", expired = true),
            event("t1", "jira", "NA-123 Fix login", minutesAgo = 5),
            event("t2", "hub", "PSG-9 Landing", minutesAgo = 60)
        )
        val config = (
            PanelConfigParser.parse(
                """
                {"schemaVersion":1,"widgets":[
                  {"id":"agents","type":"agents","pos":{"x":0,"y":0,"w":4,"h":4}},
                  {"id":"tickets","type":"feed","title":"Tickets","pos":{"x":4,"y":0,"w":4,"h":4},"params":{"sources":["jira","hub"]}}
                ]}
                """.trimIndent()
            ) as ConfigParseResult.Valid
            ).config
        val data = data(now = Instant.ofEpochMilli(now), events = events)
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        val agentRows = composeRule.onAllNodes(hasTestTagStartingWith(AGENT_ROW_TAG)).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf("agent_row_s2", "agent_row_s1", "agent_row_s3"), agentRows)
        composeRule.onNodeWithText("old session").assertDoesNotExist()
        composeRule.onNodeWithText("jira · 5m").assertExists()
        composeRule.onNodeWithText("hub · 1h").assertExists()
    }

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }

    @Test
    fun worldClockShowsEachCityWithItsDayOffset() {
        val config = (
            PanelConfigParser.parse(
                """
                {"schemaVersion":1,"widgets":[{"id":"world","type":"world_clock","pos":{"x":0,"y":0,"w":4,"h":4},"params":{"zones":[
                  {"label":"Valencia","zone":"Europe/Madrid"},{"label":"Santiago","zone":"America/Santiago"},{"label":"Sydney","zone":"Australia/Sydney"}]}}]}
                """.trimIndent()
            ) as ConfigParseResult.Valid
            ).config
        // 19:30 in Guayaquil (UTC-5) on Saturday 7 Nov 2026.
        val data = data(now = Instant.parse("2026-11-08T00:30:00Z"), zone = ZoneId.of("America/Guayaquil"))
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        composeRule.onNodeWithText("01:30").assertExists()
        composeRule.onNodeWithText("21:30").assertExists()
        composeRule.onNodeWithText("11:30").assertExists()
        assertEquals(2, composeRule.onAllNodesWithText("+1").fetchSemanticsNodes().size)
    }

    @Test
    fun weatherShowsTemperatureSkyAndDetails() {
        val config = (
            PanelConfigParser.parse(
                """
                {"schemaVersion":1,"widgets":[{"id":"gye","type":"weather","pos":{"x":0,"y":0,"w":4,"h":4},
                  "params":{"name":"Guayaquil","latitude":-2.19,"longitude":-79.89}}]}
                """.trimIndent()
            ) as ConfigParseResult.Valid
            ).config
        val reading = WeatherStatus(
            key = "-2.19,-79.89",
            temperatureC = 27.4,
            apparentTemperatureC = 30.1,
            weatherCode = 3,
            maxC = 31.2,
            minC = 22.8,
            rainProbabilityMax = 40,
            checkedAtMillis = 1
        )
        val weather = mapOf(reading.key to reading)
        val data = data(weather = weather)
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        composeRule.onNodeWithTag(WEATHER_TEMP_TAG).assertTextEquals("27°")
        composeRule.onNodeWithText("Cloudy").assertExists()
        composeRule.onNodeWithText("feels 30° · ↑31° ↓23° · rain 40%").assertExists()
    }

    @Test
    fun xFollowersShowsTheCountAndTheDailyChange() {
        val config = (
            PanelConfigParser.parse(
                """
                {"schemaVersion":1,"widgets":[{"id":"x","type":"x_followers","pos":{"x":0,"y":0,"w":4,"h":4},
                  "params":{"username":"SomeOne","tokenSecret":"x"}}]}
                """.trimIndent()
            ) as ConfigParseResult.Valid
            ).config
        val metrics =
            mapOf("x:someone" to MetricStatus("x:someone", value = 12_345, changeLastDay = 27, checkedAtMillis = 1))
        val data = data(metrics = metrics)
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Ready(config), data = data, onAction = {}) }

        composeRule.onNodeWithTag(METRIC_VALUE_TAG).assertTextEquals("12,345")
        composeRule.onNodeWithText("+27 in 24 h").assertExists()
        composeRule.onNodeWithText("X · @SOMEONE").assertExists()
    }

    @Test
    fun webWidgetEmbedsValidUrlsAndRejectsOthers() {
        val config = (
            PanelConfigParser.parse(
                """
                {"schemaVersion":1,"widgets":[
                  {"id":"music","type":"web","pos":{"x":0,"y":0,"w":4,"h":4},"params":{"url":"https://open.spotify.com/embed/playlist/abc"}}]}
                """.trimIndent()
            ) as ConfigParseResult.Valid
            ).config
        val invalid = PanelConfig(
            schemaVersion = 1,
            widgets = listOf(
                WidgetConfig(
                    "bad",
                    "web",
                    WidgetPosition(0, 0, 4, 4),
                    params = JsonObject(mapOf("url" to JsonPrimitive("http://x")))
                )
            )
        )
        val state = mutableStateOf<PanelUiState>(PanelUiState.Ready(config))
        val data = data()
        composeRule.setContent { PanelScreen(uiState = state.value, data = data, onAction = {}) }

        composeRule.onNodeWithTag(WEB_TAG).assertExists()
        state.value = PanelUiState.Ready(invalid)
        composeRule.onNodeWithText("Invalid parameter: params.url").assertExists()
    }

    @Test
    fun emptyConfigShowsAHint() {
        composeRule.setContent {
            PanelScreen(uiState = PanelUiState.Ready(PanelConfig(schemaVersion = 1)), data = data(), onAction = {})
        }

        composeRule.onNodeWithText("No widgets configured yet.").assertExists()
    }

    @Test
    fun errorStateSendsRetry() {
        val actions = mutableListOf<PanelAction>()
        val data = data()
        composeRule.setContent { PanelScreen(uiState = PanelUiState.Error, data = data, onAction = { actions += it }) }

        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf(PanelAction.Retry), actions)
    }
}
