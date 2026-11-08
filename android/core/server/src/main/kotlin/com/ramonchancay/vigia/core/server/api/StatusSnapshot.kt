package com.ramonchancay.vigia.core.server.api

import com.ramonchancay.vigia.core.data.config.PanelConfigEditor
import com.ramonchancay.vigia.core.data.events.EventRepository
import com.ramonchancay.vigia.core.data.status.StatusRepository
import com.ramonchancay.vigia.core.device.DeviceStatus
import com.ramonchancay.vigia.core.device.DeviceStatusRepository
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.StoredEvent
import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.core.model.WidgetTypes
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** `GET /status`: what each widget shows right now, as JSON, so an agent can read the panel without a screenshot. */
class StatusSnapshot @Inject constructor(
    private val editor: PanelConfigEditor,
    private val status: StatusRepository,
    private val device: DeviceStatusRepository,
    private val events: EventRepository,
    private val clock: Clock
) {
    suspend fun current(): JsonObject = build(
        config = editor.current().config,
        uptime = status.uptime.first(),
        workflows = status.workflows.first(),
        device = device.status().first(),
        events = events.active(),
        nowMillis = clock.millis()
    )

    companion object {
        private val json = Json { encodeDefaults = false }

        fun build(
            config: PanelConfig,
            uptime: Map<String, UptimeStatus>,
            workflows: Map<String, WorkflowRunStatus>,
            device: DeviceStatus?,
            nowMillis: Long,
            events: List<StoredEvent> = emptyList()
        ): JsonObject = buildJsonObject {
            put("generatedAt", Instant.ofEpochMilli(nowMillis).toString())
            val active = events.filter { it.expiresAtMillis > nowMillis }
            put(
                "widgets",
                buildJsonObject {
                    config.widgets.forEach { put(it.id, widget(it, uptime, workflows, device, active, nowMillis)) }
                }
            )
        }

        private fun widget(
            widget: WidgetConfig,
            uptime: Map<String, UptimeStatus>,
            workflows: Map<String, WorkflowRunStatus>,
            device: DeviceStatus?,
            events: List<StoredEvent>,
            nowMillis: Long
        ): JsonObject = buildJsonObject {
            put("type", widget.type)
            widget.title?.let { put("title", it) }
            if (WidgetParams.validate(widget).isNotEmpty()) {
                put("error", "invalid params")
                return@buildJsonObject
            }
            when (widget.type) {
                WidgetTypes.CLOCK -> put("now", Instant.ofEpochMilli(nowMillis).toString())

                WidgetTypes.NOTE -> put("text", WidgetParams.note(widget).value?.text)

                WidgetTypes.DEVICE -> put("device", device?.toJson() ?: JsonNull)

                WidgetTypes.UPTIME -> put(
                    "targets",
                    buildJsonArray {
                        WidgetParams.uptime(widget).value?.targets?.forEach { target ->
                            add(
                                buildJsonObject {
                                    put("name", target.name)
                                    put("url", target.url)
                                    put(
                                        "status",
                                        uptime[target.url]?.let {
                                            json.encodeToJsonElement(UptimeStatus.serializer(), it)
                                        }
                                            ?: JsonNull
                                    )
                                }
                            )
                        }
                    }
                )

                WidgetTypes.GITHUB_ACTIONS -> {
                    val params = WidgetParams.githubActions(widget).value
                    put(
                        "repos",
                        buildJsonArray {
                            params?.repos?.forEach { repo ->
                                val run = workflows[WorkflowRunStatus.keyOf(repo, params.branch, params.workflow)]
                                add(
                                    buildJsonObject {
                                        put("repo", repo)
                                        put(
                                            "run",
                                            run?.let { json.encodeToJsonElement(WorkflowRunStatus.serializer(), it) }
                                                ?: JsonNull
                                        )
                                    }
                                )
                            }
                        }
                    )
                }

                WidgetTypes.AGENTS -> {
                    val source = WidgetParams.agents(widget).value?.source
                    put("sessions", eventsJson(events.filter { it.source == source }))
                }

                WidgetTypes.FEED -> {
                    val params = WidgetParams.feed(widget).value
                    put(
                        "items",
                        eventsJson(
                            events.filter {
                                params != null && it.source in params.sources
                            }.take(params?.max ?: 0)
                        )
                    )
                }

                else -> put("error", "unsupported type")
            }
        }

        private fun eventsJson(events: List<StoredEvent>) =
            buildJsonArray { events.forEach { add(json.encodeToJsonElement(StoredEvent.serializer(), it)) } }

        private fun DeviceStatus.toJson(): JsonElement = buildJsonObject {
            put(
                "battery",
                battery?.let {
                    buildJsonObject {
                        put("levelPercent", it.levelPercent)
                        put("temperatureCelsius", it.temperatureCelsius)
                        put("charging", it.isCharging)
                    }
                } ?: JsonNull
            )
            put(
                "memory",
                buildJsonObject {
                    put("usedBytes", memory.usedBytes)
                    put("totalBytes", memory.totalBytes)
                }
            )
            put(
                "wifi",
                buildJsonObject {
                    put("connected", wifi.isConnected)
                    put("signalLevel", wifi.signalLevel)
                }
            )
        }
    }
}
