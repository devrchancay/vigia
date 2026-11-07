package com.ramonchancay.vigia.core.server.api

import com.ramonchancay.vigia.core.data.config.ConfigVersion
import com.ramonchancay.vigia.core.data.config.ConfigWriteResult
import com.ramonchancay.vigia.core.data.config.PanelConfigEditor
import com.ramonchancay.vigia.core.data.secrets.SecretStore
import com.ramonchancay.vigia.core.model.PanelConfig
import com.ramonchancay.vigia.core.model.PanelConfigParser
import com.ramonchancay.vigia.core.model.WidgetParams
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readBuffer
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.readByteArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** Everything the API needs, so it can run in the real server and in tests alike. */
class ApiDependencies(
    val editor: PanelConfigEditor,
    val secrets: SecretStore,
    /** Current content of every widget for `GET /status`. */
    val statusSnapshot: suspend () -> JsonObject,
    val tokenMatches: suspend (String) -> Boolean,
    /** Whether a remote IP literal may call the API; see [IpAllowlist]. */
    val isAllowed: (String) -> Boolean,
    val appVersion: String,
    val uptimeSeconds: () -> Long,
    val requestsPerMinute: Int = 120
)

const val API_PREFIX = "/api/v1"
const val AUTHOR_HEADER = "X-Vigia-Author"
private const val DEFAULT_AUTHOR = "api"
private const val MAX_BODY_BYTES = 64 * 1024L
private val API_RATE_LIMIT = RateLimitName("api")

private val responseJson = Json {
    encodeDefaults = false
    explicitNulls = false
}

/** The HTTP API from docs/SPEC.md section 5 (config endpoints, milestone M2). */
fun Application.vigiaApi(deps: ApiDependencies) {
    install(ContentNegotiation) { json(responseJson) }
    install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
            call.respondError(HttpStatusCode.PayloadTooLarge, "payload_too_large", "Body exceeds $MAX_BODY_BYTES bytes")
        }
        exception<Throwable> { call, cause ->
            Timber.e(cause, "Unhandled API error")
            call.respondError(HttpStatusCode.InternalServerError, "internal_error", "Unexpected server error")
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(HttpStatusCode.NotFound, "not_found", "No such route")
        }
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respondError(HttpStatusCode.TooManyRequests, "rate_limited", "Too many requests, slow down")
        }
    }
    install(RateLimit) {
        register(API_RATE_LIMIT) {
            rateLimiter(limit = deps.requestsPerMinute, refillPeriod = 60.seconds)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }

    routing {
        route(API_PREFIX) {
            install(allowlistPlugin(deps.isAllowed))
            rateLimit(API_RATE_LIMIT) {
                // Liveness only: reveals no config or device data, so it needs no token.
                get("/health") {
                    call.respond(HealthBody(status = "ok", version = deps.appVersion, uptimeSec = deps.uptimeSeconds()))
                }
                configRoutes(deps.editor, deps.tokenMatches)
                secretRoutes(deps.secrets, deps.tokenMatches)
                route("/status") {
                    install(bearerAuthPlugin(deps.tokenMatches))
                    get { call.respondText(deps.statusSnapshot().toString(), ContentType.Application.Json) }
                }
            }
        }
    }
}

private fun Route.configRoutes(editor: PanelConfigEditor, tokenMatches: suspend (String) -> Boolean) {
    route("/config") {
        install(bearerAuthPlugin(tokenMatches))
        get { call.respondConfig(editor.current().config) }
        put { call.respondWrite(editor.replace(call.receiveLimitedText(), call.author())) }
        patch { call.respondWrite(editor.patch(call.receiveLimitedText(), call.author())) }
        post("/validate") {
            when (val result = editor.validate(call.receiveLimitedText(), call.author())) {
                is ConfigWriteResult.Accepted -> call.respond(ValidationBody(valid = true))

                is ConfigWriteResult.Rejected -> call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    result.errors.toBody()
                )
            }
        }
        post("/rollback") {
            when (val result = editor.rollback(call.author())) {
                is ConfigWriteResult.Accepted -> call.respondVersion(result.version)
                is ConfigWriteResult.Rejected -> call.respond(HttpStatusCode.Conflict, result.errors.toBody())
            }
        }
        get("/history") {
            call.respondText(
                responseJson.encodeToString(VERSION_LIST_SERIALIZER, editor.history()),
                ContentType.Application.Json
            )
        }
    }
}

/** Write-only: names can be listed, values are never returned. */
private fun Route.secretRoutes(secrets: SecretStore, tokenMatches: suspend (String) -> Boolean) {
    route("/secrets") {
        install(bearerAuthPlugin(tokenMatches))
        get { call.respond(SecretNamesBody(secrets.names())) }
        put("/{name}") {
            val name = call.secretName() ?: return@put
            val value = call.receiveLimitedText().trim()
            when {
                value.isEmpty() -> call.respondError(
                    HttpStatusCode.UnprocessableEntity,
                    "invalid_format",
                    "Secret value is empty"
                )

                value.length > SecretStore.MAX_VALUE_LENGTH -> call.respondError(
                    HttpStatusCode.UnprocessableEntity,
                    "out_of_range",
                    "Secret longer than ${SecretStore.MAX_VALUE_LENGTH} characters"
                )

                else -> {
                    secrets.put(name, value)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        delete("/{name}") {
            val name = call.secretName() ?: return@delete
            if (secrets.delete(name)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respondError(HttpStatusCode.NotFound, "not_found", "No secret named '$name'")
            }
        }
    }
}

/** The `{name}` path parameter when valid; otherwise responds 422 and returns null. */
private suspend fun ApplicationCall.secretName(): String? {
    val name = parameters["name"].orEmpty()
    if (WidgetParams.SECRET_NAME.matches(name)) return name
    respondError(
        HttpStatusCode.UnprocessableEntity,
        "invalid_format",
        "Secret names are 1-64 chars of a-z, 0-9, '-' or '_'"
    )
    return null
}

private val VERSION_LIST_SERIALIZER = ListSerializer(ConfigVersion.serializer())

private suspend fun ApplicationCall.respondWrite(result: ConfigWriteResult) = when (result) {
    is ConfigWriteResult.Accepted -> respondVersion(result.version)
    is ConfigWriteResult.Rejected -> respond(HttpStatusCode.UnprocessableEntity, result.errors.toBody())
}

/** The config document exactly as stored, so a GET can be edited and sent back with PUT. */
private suspend fun ApplicationCall.respondConfig(config: PanelConfig) =
    respondText(PanelConfigParser.encode(config), ContentType.Application.Json)

private suspend fun ApplicationCall.respondVersion(version: ConfigVersion) =
    respondText(responseJson.encodeToString(ConfigVersion.serializer(), version), ContentType.Application.Json)

internal suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ApiErrorBody(ApiError(code = code, message = message)))

private fun ApplicationCall.author(): String =
    request.headers[AUTHOR_HEADER]?.trim()?.take(64)?.takeIf { it.isNotEmpty() } ?: DEFAULT_AUTHOR

private class PayloadTooLargeException : RuntimeException()

/** Reads the body up to [MAX_BODY_BYTES]; larger bodies fail with 413 whether or not Content-Length is sent. */
private suspend fun ApplicationCall.receiveLimitedText(): String {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > MAX_BODY_BYTES) throw PayloadTooLargeException()
    val bytes = receiveChannel().readBuffer(MAX_BODY_BYTES + 1).readByteArray()
    if (bytes.size > MAX_BODY_BYTES) throw PayloadTooLargeException()
    return bytes.decodeToString()
}

private fun allowlistPlugin(isAllowed: (String) -> Boolean) = createRouteScopedPlugin("IpAllowlist") {
    onCall { call ->
        if (!isAllowed(call.request.origin.remoteAddress)) {
            call.respondError(
                HttpStatusCode.Forbidden,
                "forbidden",
                "Address not allowed; use Tailscale or adb forward"
            )
        }
    }
}

private fun bearerAuthPlugin(tokenMatches: suspend (String) -> Boolean) = createRouteScopedPlugin("BearerAuth") {
    onCall { call ->
        val header = call.request.headers[HttpHeaders.Authorization].orEmpty()
        val token = header.removePrefix("Bearer ").takeIf { header.startsWith("Bearer ") }
        if (token == null || !tokenMatches(token)) {
            call.response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer")
            call.respondError(HttpStatusCode.Unauthorized, "unauthorized", "Missing or invalid bearer token")
        }
    }
}
