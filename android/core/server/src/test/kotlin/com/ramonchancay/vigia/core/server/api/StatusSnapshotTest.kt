package com.ramonchancay.vigia.core.server.api

import com.ramonchancay.vigia.core.device.BatteryStatus
import com.ramonchancay.vigia.core.device.DeviceStatus
import com.ramonchancay.vigia.core.device.MemoryStatus
import com.ramonchancay.vigia.core.device.WifiStatus
import com.ramonchancay.vigia.core.model.ConfigParseResult
import com.ramonchancay.vigia.core.model.PanelConfigParser
import com.ramonchancay.vigia.core.model.StoredEvent
import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusSnapshotTest {
    private val config = (
        PanelConfigParser.parse(
            """
            {"schemaVersion":1,"widgets":[
              {"id":"clock","type":"clock","pos":{"x":0,"y":0,"w":1,"h":1}},
              {"id":"todo","type":"note","title":"Today","pos":{"x":1,"y":0,"w":1,"h":1},"params":{"text":"Ship M3"}},
              {"id":"device","type":"device","pos":{"x":2,"y":0,"w":1,"h":1}},
              {"id":"sites","type":"uptime","pos":{"x":3,"y":0,"w":1,"h":1},"params":{"targets":[
                {"name":"site","url":"https://example.com"},{"name":"new","url":"https://new.example.com"}]}},
              {"id":"ci","type":"github_actions","pos":{"x":4,"y":0,"w":1,"h":1},"params":{"repos":["owner/repo"],"branch":"main"}},
              {"id":"radar","type":"hologram","pos":{"x":5,"y":0,"w":1,"h":1}},
              {"id":"tickets","type":"feed","pos":{"x":6,"y":0,"w":1,"h":1},"params":{"sources":["jira"]}}
            ]}
            """.trimIndent()
        ) as ConfigParseResult.Valid
        ).config

    @Test
    fun `describes every widget with its live data`() {
        val snapshot = StatusSnapshot.build(
            config = config,
            uptime = mapOf(
                "https://example.com" to UptimeStatus("https://example.com", true, 200, 90, null, 1_000, 500)
            ),
            workflows = mapOf(
                "owner/repo|main|" to WorkflowRunStatus(
                    key = "owner/repo|main|",
                    repo = "owner/repo",
                    status = "completed",
                    conclusion = "success",
                    checkedAtMillis = 1_000
                )
            ),
            device = DeviceStatus(
                BatteryStatus(80, 31.5f, true),
                MemoryStatus(1L shl 30, 2L shl 30),
                WifiStatus(true, 4)
            ),
            nowMillis = 0,
            events = listOf(
                StoredEvent(id = "jira|1", source = "jira", title = "NA-1", receivedAtMillis = 0, expiresAtMillis = 10),
                StoredEvent(
                    id = "jira|2",
                    source = "jira",
                    title = "expired",
                    receivedAtMillis = 0,
                    expiresAtMillis = 0
                )
            )
        )

        val widgets = snapshot.getValue("widgets").jsonObject
        assertEquals("1970-01-01T00:00:00Z", snapshot.getValue("generatedAt").jsonPrimitive.content)
        assertEquals("Ship M3", widgets.getValue("todo").jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals("Today", widgets.getValue("todo").jsonObject.getValue("title").jsonPrimitive.content)
        assertEquals(
            "80",
            widgets.getValue(
                "device"
            ).jsonObject.getValue(
                "device"
            ).jsonObject.getValue("battery").jsonObject.getValue("levelPercent").jsonPrimitive.content
        )
        val targets = widgets.getValue("sites").jsonObject.getValue("targets").jsonArray
        assertEquals(
            "200",
            targets[0].jsonObject.getValue("status").jsonObject.getValue("statusCode").jsonPrimitive.content
        )
        assertEquals(JsonNull, targets[1].jsonObject.getValue("status"))
        val run = widgets.getValue("ci").jsonObject.getValue("repos").jsonArray[0].jsonObject.getValue("run").jsonObject
        assertEquals("success", run.getValue("conclusion").jsonPrimitive.content)
        assertEquals("unsupported type", widgets.getValue("radar").jsonObject.getValue("error").jsonPrimitive.content)
        val tickets = widgets.getValue("tickets").jsonObject.getValue("items").jsonArray
        assertEquals(listOf("NA-1"), tickets.map { it.jsonObject.getValue("title").jsonPrimitive.content })
    }
}
