package com.ramonchancay.vigia.core.model

import kotlinx.serialization.Serializable

/** One parameter of a widget type, as `GET /widgets/types` describes it to clients and agents. */
@Serializable
data class ParamSpec(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val default: String? = null,
    val description: String
)

@Serializable
data class WidgetTypeSpec(val type: String, val description: String, val params: List<ParamSpec> = emptyList())

/**
 * The widget types this version renders, with their params. Agents read it to build valid configs; the rules
 * themselves live in [WidgetParams], and a unit test keeps both lists in sync.
 */
object WidgetCatalog {
    val types: List<WidgetTypeSpec> = listOf(
        WidgetTypeSpec(WidgetTypes.CLOCK, "Local time and date; one line without seconds in a one-row tile."),
        WidgetTypeSpec(
            WidgetTypes.NOTE,
            "Short static text.",
            listOf(ParamSpec("text", "string", required = true, description = "1-500 characters"))
        ),
        WidgetTypeSpec(
            WidgetTypes.DEVICE,
            "This phone: battery, battery temperature (red from 40 C), memory, Wi-Fi signal.",
            listOf(
                ParamSpec("show", "string[]", default = "all", description = "Rows: battery, batteryTemp, memory, wifi")
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.WORLD_CLOCK,
            "Time in other cities with a +1/-1 day marker.",
            listOf(
                ParamSpec(
                    "zones",
                    "{label, zone}[]",
                    required = true,
                    description = "1-6 cities; zone is an IANA id like Europe/Madrid"
                )
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.WEATHER,
            "Current weather from Open-Meteo (no key): temperature, sky, feels-like, max/min, rain probability.",
            listOf(
                ParamSpec("name", "string", required = true, description = "Label shown on the tile"),
                ParamSpec("latitude", "number", required = true, description = "-90 to 90"),
                ParamSpec("longitude", "number", required = true, description = "-180 to 180"),
                ParamSpec("intervalSec", "integer", default = "900", description = "600-3600")
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.UPTIME,
            "HTTP checks with a traffic light, latency and time in the current state.",
            listOf(
                ParamSpec(
                    "targets",
                    "{name, url, expectStatus?}[]",
                    required = true,
                    description = "1-20 http(s) URLs"
                ),
                ParamSpec("intervalSec", "integer", default = "60", description = "15-3600"),
                ParamSpec("slowMs", "integer", default = "1000", description = "Amber above this latency")
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.GITHUB_ACTIONS,
            "Latest GitHub Actions run per repo.",
            listOf(
                ParamSpec("repos", "string[]", required = true, description = "1-10 owner/repo"),
                ParamSpec("branch", "string", description = "Only runs on this branch"),
                ParamSpec("workflow", "string", description = "Workflow file name or id"),
                ParamSpec("intervalSec", "integer", default = "60", description = "30-3600"),
                ParamSpec("tokenSecret", "string", description = "Secret holding a token, for private repos")
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.AGENTS,
            "Agent sessions pushed as events (Claude Code hook): waiting for permission first, then working, then done.",
            listOf(
                ParamSpec("source", "string", default = "claude-code", description = "Event source"),
                ParamSpec("max", "integer", default = "6", description = "1-20 rows")
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.FEED,
            "Newest pushed events of some sources (tickets, leads, alerts).",
            listOf(
                ParamSpec("sources", "string[]", required = true, description = "1-10 event sources"),
                ParamSpec("max", "integer", default = "5", description = "1-20 rows"),
                ParamSpec("showBody", "boolean", default = "false", description = "Show each event's body")
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.WEB,
            "An embedded web page (e.g. a Spotify playlist embed). Links to other sites are blocked.",
            listOf(
                ParamSpec(
                    "url",
                    "string",
                    required = true,
                    description = "https URL, e.g. https://open.spotify.com/embed/playlist/<id>"
                )
            )
        ),
        WidgetTypeSpec(
            WidgetTypes.X_FOLLOWERS,
            "Follower count of an X account and its change over 24 h (official API, billed per read).",
            listOf(
                ParamSpec("username", "string", required = true, description = "Without @"),
                ParamSpec(
                    "tokenSecret",
                    "string",
                    required = true,
                    description = "Secret holding an X API bearer token"
                ),
                ParamSpec("intervalSec", "integer", default = "21600", description = "3600-86400")
            )
        )
    )
}
