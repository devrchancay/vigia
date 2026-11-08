package com.ramonchancay.vigia.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Widget types this version understands. Any other type still parses and renders as an error tile. */
object WidgetTypes {
    const val CLOCK = "clock"
    const val NOTE = "note"
    const val DEVICE = "device"
    const val UPTIME = "uptime"
    const val GITHUB_ACTIONS = "github_actions"
    const val AGENTS = "agents"
    const val FEED = "feed"
    const val WORLD_CLOCK = "world_clock"

    val DEVICE_ROWS = listOf("battery", "batteryTemp", "memory", "wifi")
}

@Serializable
data class NoteParams(val text: String)

@Serializable
data class DeviceParams(val show: List<String> = WidgetTypes.DEVICE_ROWS)

@Serializable
data class UptimeTarget(val name: String, val url: String, val expectStatus: Int? = null)

/** [slowMs]: a response slower than this shows amber. */
@Serializable
data class UptimeParams(val targets: List<UptimeTarget>, val intervalSec: Int = 60, val slowMs: Int = 1_000)

/**
 * Latest run per repo. [workflow] is a workflow file name (`ci.yml`) or id; [tokenSecret] names a secret
 * stored with `PUT /secrets/{name}` (needed for private repos and to lift the 60 requests/hour anonymous limit).
 */
@Serializable
data class GithubActionsParams(
    val repos: List<String>,
    val branch: String? = null,
    val workflow: String? = null,
    val intervalSec: Int = 60,
    val tokenSecret: String? = null
)

/** Sessions pushed by an agent hook (Claude Code by default); one row per event key. */
@Serializable
data class AgentsParams(val source: String = "claude-code", val max: Int = 6)

/** Newest events of [sources]; e.g. tickets pushed by a Jira script use `sources: ["jira"]`. */
@Serializable
data class FeedParams(val sources: List<String>, val max: Int = 5, val showBody: Boolean = false)

/** A city on the world clock: [zone] is an IANA time zone id such as `Europe/Madrid`. */
@Serializable
data class WorldZone(val label: String, val zone: String)

@Serializable
data class WorldClockParams(val zones: List<WorldZone>)

/** Decoding and checking of `params` per widget type. Errors carry paths relative to the widget (`params.x`). */
object WidgetParams {
    private val json = Json { ignoreUnknownKeys = false }

    val SECRET_NAME = Regex("""[a-z0-9][a-z0-9_-]{0,63}""")
    private val REPO = Regex("""[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}""")
    val UPTIME_INTERVAL_RANGE = 15..3_600
    val GITHUB_INTERVAL_RANGE = 30..3_600
    const val MAX_UPTIME_TARGETS = 20
    const val MAX_REPOS = 10
    const val MAX_NOTE_LENGTH = 500
    const val MAX_ROWS = 20
    const val MAX_WORLD_ZONES = 6

    fun note(widget: WidgetConfig): Decoded<NoteParams> = decode(widget, NoteParams.serializer()) { params, errors ->
        if (params.text.isBlank()) errors += error(ConfigError.Code.InvalidFormat, "text", "Must not be blank")
        if (params.text.length > MAX_NOTE_LENGTH) {
            errors += error(ConfigError.Code.OutOfRange, "text", "At most $MAX_NOTE_LENGTH characters")
        }
    }

    fun device(widget: WidgetConfig): Decoded<DeviceParams> = decode(widget, DeviceParams.serializer()) {
            params,
            errors
        ->
        params.show.forEachIndexed { i, row ->
            if (row !in WidgetTypes.DEVICE_ROWS) {
                errors += error(
                    ConfigError.Code.InvalidFormat,
                    "show[$i]",
                    "Unknown row '$row'; use ${WidgetTypes.DEVICE_ROWS.joinToString()}"
                )
            }
        }
    }

    fun uptime(widget: WidgetConfig): Decoded<UptimeParams> = decode(widget, UptimeParams.serializer()) {
            params,
            errors
        ->
        if (params.targets.isEmpty() || params.targets.size > MAX_UPTIME_TARGETS) {
            errors += error(ConfigError.Code.OutOfRange, "targets", "Between 1 and $MAX_UPTIME_TARGETS targets")
        }
        params.targets.forEachIndexed { i, target ->
            if (target.name.isBlank() || target.name.length > 40) {
                errors += error(ConfigError.Code.InvalidFormat, "targets[$i].name", "1-40 characters")
            }
            if (!isHttpUrl(target.url)) {
                errors += error(ConfigError.Code.InvalidFormat, "targets[$i].url", "Must be an http(s) URL with a host")
            }
            target.expectStatus?.let {
                if (it !in 100..599) errors += error(ConfigError.Code.OutOfRange, "targets[$i].expectStatus", "100-599")
            }
        }
        if (params.intervalSec !in UPTIME_INTERVAL_RANGE) {
            errors +=
                error(
                    ConfigError.Code.OutOfRange,
                    "intervalSec",
                    "Between ${UPTIME_INTERVAL_RANGE.first} and ${UPTIME_INTERVAL_RANGE.last}"
                )
        }
        if (params.slowMs !in 50..60_000) errors += error(ConfigError.Code.OutOfRange, "slowMs", "Between 50 and 60000")
    }

    fun githubActions(widget: WidgetConfig): Decoded<GithubActionsParams> =
        decode(widget, GithubActionsParams.serializer()) { params, errors ->
            if (params.repos.isEmpty() || params.repos.size > MAX_REPOS) {
                errors += error(ConfigError.Code.OutOfRange, "repos", "Between 1 and $MAX_REPOS repos")
            }
            params.repos.forEachIndexed { i, repo ->
                if (!REPO.matches(repo)) {
                    errors +=
                        error(ConfigError.Code.InvalidFormat, "repos[$i]", "Must be owner/repo")
                }
            }
            if (params.branch?.isBlank() ==
                true
            ) {
                errors += error(ConfigError.Code.InvalidFormat, "branch", "Must not be blank")
            }
            if (params.workflow?.isBlank() == true) {
                errors += error(ConfigError.Code.InvalidFormat, "workflow", "Must not be blank")
            }
            if (params.intervalSec !in GITHUB_INTERVAL_RANGE) {
                errors +=
                    error(
                        ConfigError.Code.OutOfRange,
                        "intervalSec",
                        "Between ${GITHUB_INTERVAL_RANGE.first} and ${GITHUB_INTERVAL_RANGE.last}"
                    )
            }
            params.tokenSecret?.let {
                if (!SECRET_NAME.matches(it)) {
                    errors +=
                        error(ConfigError.Code.InvalidFormat, "tokenSecret", "Not a valid secret name")
                }
            }
        }

    fun agents(widget: WidgetConfig): Decoded<AgentsParams> = decode(widget, AgentsParams.serializer()) {
            params,
            errors
        ->
        if (!SECRET_NAME.matches(params.source)) {
            errors +=
                error(ConfigError.Code.InvalidFormat, "source", "Not a valid source name")
        }
        if (params.max !in 1..MAX_ROWS) errors += error(ConfigError.Code.OutOfRange, "max", "Between 1 and $MAX_ROWS")
    }

    fun feed(widget: WidgetConfig): Decoded<FeedParams> = decode(widget, FeedParams.serializer()) { params, errors ->
        if (params.sources.isEmpty() ||
            params.sources.size > 10
        ) {
            errors +=
                error(ConfigError.Code.OutOfRange, "sources", "Between 1 and 10 sources")
        }
        params.sources.forEachIndexed { i, source ->
            if (!SECRET_NAME.matches(source)) {
                errors +=
                    error(ConfigError.Code.InvalidFormat, "sources[$i]", "Not a valid source name")
            }
        }
        if (params.max !in 1..MAX_ROWS) errors += error(ConfigError.Code.OutOfRange, "max", "Between 1 and $MAX_ROWS")
    }

    fun worldClock(widget: WidgetConfig): Decoded<WorldClockParams> = decode(widget, WorldClockParams.serializer()) {
            params,
            errors
        ->
        if (params.zones.isEmpty() || params.zones.size > MAX_WORLD_ZONES) {
            errors += error(ConfigError.Code.OutOfRange, "zones", "Between 1 and $MAX_WORLD_ZONES zones")
        }
        params.zones.forEachIndexed { i, zone ->
            if (zone.label.isBlank() ||
                zone.label.length > 24
            ) {
                errors +=
                    error(ConfigError.Code.InvalidFormat, "zones[$i].label", "1-24 characters")
            }
            val valid = runCatching { java.time.ZoneId.of(zone.zone) }.isSuccess
            if (!valid) {
                errors +=
                    error(
                        ConfigError.Code.InvalidFormat,
                        "zones[$i].zone",
                        "Unknown time zone '${zone.zone}', e.g. Europe/Madrid"
                    )
            }
        }
    }

    /** Params errors for a known [WidgetConfig.type]; empty for valid params and for unknown types. */
    fun validate(widget: WidgetConfig): List<ConfigError> = when (widget.type) {
        WidgetTypes.NOTE -> note(widget).errors
        WidgetTypes.DEVICE -> device(widget).errors
        WidgetTypes.UPTIME -> uptime(widget).errors
        WidgetTypes.GITHUB_ACTIONS -> githubActions(widget).errors
        WidgetTypes.AGENTS -> agents(widget).errors
        WidgetTypes.FEED -> feed(widget).errors
        WidgetTypes.WORLD_CLOCK -> worldClock(widget).errors
        else -> emptyList()
    }

    /** Either the decoded [value] (when [errors] is empty) or the problems found. */
    class Decoded<T>(val value: T?, val errors: List<ConfigError>) {
        val isValid: Boolean get() = value != null && errors.isEmpty()
    }

    private fun <T> decode(
        widget: WidgetConfig,
        serializer: KSerializer<T>,
        check: (T, MutableList<ConfigError>) -> Unit
    ): Decoded<T> {
        val value = try {
            json.decodeFromJsonElement(serializer, widget.params)
        } catch (e: SerializationException) {
            return Decoded(
                null,
                listOf(error(ConfigError.Code.MalformedJson, "", e.message.orEmpty().lineSequence().first()))
            )
        } catch (e: IllegalArgumentException) {
            return Decoded(
                null,
                listOf(error(ConfigError.Code.MalformedJson, "", e.message.orEmpty().lineSequence().first()))
            )
        }
        val errors = mutableListOf<ConfigError>().also { check(value, it) }
        return Decoded(value.takeIf { errors.isEmpty() }, errors)
    }

    private fun error(code: ConfigError.Code, field: String, message: String) =
        ConfigError(code, if (field.isEmpty()) "params" else "params.$field", message)

    private fun isHttpUrl(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrEmpty()
    }
}
