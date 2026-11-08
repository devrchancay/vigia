package com.ramonchancay.vigia.core.model

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EventLevel {
    @SerialName("info")
    Info,

    @SerialName("ok")
    Ok,

    @SerialName("warn")
    Warn,

    @SerialName("error")
    Error
}

/**
 * Something a script, hook or agent pushes to the panel (`POST /events`, docs/SPEC.md section 5). A new event
 * with the same [source] and [key] replaces the previous one, which is how an agent row moves from working to
 * waiting to done. [at] is when it happens (meetings, deadlines), ISO-8601; [ttlSec] how long it stays.
 */
@Serializable
data class PanelEvent(
    val source: String,
    val title: String,
    val key: String? = null,
    val kind: String? = null,
    val body: String? = null,
    val level: EventLevel = EventLevel.Info,
    val ttlSec: Int = DEFAULT_TTL_SEC,
    val url: String? = null,
    val at: String? = null
) {
    companion object {
        const val DEFAULT_TTL_SEC = 24 * 60 * 60
        val TTL_RANGE = 10..7 * 24 * 60 * 60
        const val MAX_BATCH = 50
        private val SOURCE = Regex("""[a-z0-9][a-z0-9_-]{0,63}""")
        private val KIND = Regex("""[a-z0-9_]{1,40}""")

        /** Errors for one event; [prefix] is its path in the request (`events[2]`, or empty for a single event). */
        fun validate(event: PanelEvent, prefix: String = ""): List<ConfigError> = buildList {
            fun path(field: String) = if (prefix.isEmpty()) field else "$prefix.$field"
            if (!SOURCE.matches(event.source)) {
                add(ConfigError(ConfigError.Code.InvalidFormat, path("source"), "1-64 chars of a-z, 0-9, '-' or '_'"))
            }
            if (event.title.isBlank() || event.title.length > 200) {
                add(ConfigError(ConfigError.Code.OutOfRange, path("title"), "1-200 characters"))
            }
            event.key?.let {
                if (it.isBlank() ||
                    it.length > 128
                ) {
                    add(ConfigError(ConfigError.Code.OutOfRange, path("key"), "1-128 characters"))
                }
            }
            event.kind?.let {
                if (!KIND.matches(
                        it
                    )
                ) {
                    add(ConfigError(ConfigError.Code.InvalidFormat, path("kind"), "1-40 chars of a-z, 0-9 or '_'"))
                }
            }
            event.body?.let {
                if (it.length >
                    1_000
                ) {
                    add(ConfigError(ConfigError.Code.OutOfRange, path("body"), "At most 1000 characters"))
                }
            }
            if (event.ttlSec !in TTL_RANGE) {
                add(
                    ConfigError(
                        ConfigError.Code.OutOfRange,
                        path("ttlSec"),
                        "Between ${TTL_RANGE.first} and ${TTL_RANGE.last}"
                    )
                )
            }
            event.url?.let { url ->
                val ok = url.length <= 500 && (url.startsWith("https://") || url.startsWith("http://"))
                if (!ok) {
                    add(
                        ConfigError(
                            ConfigError.Code.InvalidFormat,
                            path("url"),
                            "An http(s) URL of at most 500 characters"
                        )
                    )
                }
            }
            event.at?.let {
                if (parseInstant(it) ==
                    null
                ) {
                    add(
                        ConfigError(
                            ConfigError.Code.InvalidFormat,
                            path("at"),
                            "ISO-8601 instant, e.g. 2026-11-07T15:00:00Z"
                        )
                    )
                }
            }
        }

        /** Accepts `2026-11-07T15:00:00Z` and offsets like `2026-11-07T10:00:00-05:00`. */
        fun parseInstant(text: String): Instant? = try {
            java.time.OffsetDateTime.parse(text).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

/** A stored event: [id] is `source|key` (or a generated id without key), with server-side times in epoch millis. */
@Serializable
data class StoredEvent(
    val id: String,
    val source: String,
    val key: String? = null,
    val kind: String? = null,
    val title: String,
    val body: String? = null,
    val level: EventLevel = EventLevel.Info,
    val url: String? = null,
    val atMillis: Long? = null,
    val receivedAtMillis: Long,
    val expiresAtMillis: Long
)
