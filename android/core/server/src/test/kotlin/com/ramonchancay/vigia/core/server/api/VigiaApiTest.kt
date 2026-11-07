package com.ramonchancay.vigia.core.server.api

import androidx.datastore.core.DataStoreFactory
import com.ramonchancay.vigia.core.data.config.ConfigArchive
import com.ramonchancay.vigia.core.data.config.StoredPanelConfigRepository
import com.ramonchancay.vigia.core.data.secrets.SecretStore
import com.ramonchancay.vigia.core.model.PanelConfig
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VigiaApiTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val token = "test-token"

    /** In-memory secrets: the API layer is what is under test here. */
    private val secrets = object : SecretStore {
        val values = mutableMapOf<String, String>()

        override suspend fun put(name: String, value: String) {
            values[name] = value
        }

        override suspend fun delete(name: String) = values.remove(name) != null

        override suspend fun get(name: String) = values[name]

        override suspend fun names() = values.keys.sorted()
    }

    @After
    fun tearDown() = storeScope.cancel()

    private fun ApplicationTestBuilder.setUp(allowed: Boolean = true, requestsPerMinute: Int = 120) {
        val editor = StoredPanelConfigRepository(
            dataStore = DataStoreFactory.create(
                serializer = ArchiveSerializer,
                scope = storeScope,
                produceFile = { File(folder.root, "archive.json") }
            ),
            defaultConfig = { PanelConfig(schemaVersion = 1) },
            clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)
        )
        application {
            vigiaApi(
                ApiDependencies(
                    editor = editor,
                    secrets = secrets,
                    tokenMatches = { it == token },
                    isAllowed = { allowed },
                    appVersion = "1.2.3",
                    uptimeSeconds = { 42 },
                    requestsPerMinute = requestsPerMinute
                )
            )
        }
    }

    private suspend fun ApplicationTestBuilder.authed(path: String): HttpResponse =
        client.get(path) { header(HttpHeaders.Authorization, "Bearer $token") }

    private fun HttpResponse.json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `health needs no token and reveals no config`() = testApplication {
        setUp()

        val response = client.get("$API_PREFIX/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok","version":"1.2.3","uptimeSec":42}""", response.bodyAsText())
    }

    @Test
    fun `config routes need a valid bearer token`() = testApplication {
        setUp()

        val missing = client.get("$API_PREFIX/config")
        val wrong = client.get("$API_PREFIX/config") { header(HttpHeaders.Authorization, "Bearer nope") }

        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals("Bearer", missing.headers[HttpHeaders.WWWAuthenticate])
        assertEquals(
            "unauthorized",
            missing.json(missing.bodyAsText())["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun `addresses outside the allowlist are forbidden, even for health`() = testApplication {
        setUp(allowed = false)

        assertEquals(HttpStatusCode.Forbidden, client.get("$API_PREFIX/health").status)
        assertEquals(HttpStatusCode.Forbidden, authed("$API_PREFIX/config").status)
    }

    @Test
    fun `get returns the current config document`() = testApplication {
        setUp()

        val response = authed("$API_PREFIX/config")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"schemaVersion":1}""", response.bodyAsText())
    }

    @Test
    fun `put stores a valid config and records the author`() = testApplication {
        setUp()

        val response = client.put("$API_PREFIX/config") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(AUTHOR_HEADER, "claude")
            setBody("""{"schemaVersion":1,"grid":{"columns":6,"rows":3}}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json(response.bodyAsText())
        assertEquals("claude", body["author"]!!.jsonPrimitive.content)
        assertEquals("""{"schemaVersion":1,"grid":{"columns":6,"rows":3}}""", authed("$API_PREFIX/config").bodyAsText())
    }

    @Test
    fun `invalid config is rejected with every error and its path`() = testApplication {
        setUp()

        val response = client.put("$API_PREFIX/config") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"schemaVersion":1,"grid":{"columns":0,"rows":99}}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.json(response.bodyAsText())
        assertEquals("grid.columns", body["error"]!!.jsonObject["path"]!!.jsonPrimitive.content)
        assertEquals("out_of_range", body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(2, body["errors"]!!.jsonArray.size)
    }

    @Test
    fun `patch merges, validate dry-runs, history and rollback undo`() = testApplication {
        setUp()
        val auth = HttpHeaders.Authorization to "Bearer $token"

        val patched = client.patch("$API_PREFIX/config") {
            header(auth.first, auth.second)
            setBody("""{"theme":{"palette":"teal"}}""")
        }
        assertEquals(HttpStatusCode.OK, patched.status)

        val validation = client.post("$API_PREFIX/config/validate") {
            header(auth.first, auth.second)
            setBody("""{"schemaVersion":1,"theme":{"palette":"neon"}}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, validation.status)

        val history = authed("$API_PREFIX/config/history")
        assertEquals(1, Json.parseToJsonElement(history.bodyAsText()).jsonArray.size)

        val rollback = client.post("$API_PREFIX/config/rollback") { header(auth.first, auth.second) }
        assertEquals(HttpStatusCode.OK, rollback.status)
        assertEquals("""{"schemaVersion":1}""", authed("$API_PREFIX/config").bodyAsText())

        val nothingLeft = client.post("$API_PREFIX/config/rollback") { header(auth.first, auth.second) }
        assertEquals(HttpStatusCode.Conflict, nothingLeft.status)
    }

    @Test
    fun `bodies over 64 KB are rejected`() = testApplication {
        setUp()

        val response = client.put("$API_PREFIX/config") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("x".repeat(64 * 1024 + 1))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `requests over the rate limit get 429`() = testApplication {
        setUp(requestsPerMinute = 2)

        val statuses = (1..3).map { client.get("$API_PREFIX/health").status }

        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.TooManyRequests), statuses)
    }

    @Test
    fun `unknown routes answer with the error format`() = testApplication {
        setUp()

        val response = authed("$API_PREFIX/nope")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"not_found\""))
    }

    @Test
    fun `secrets are write-only and validated`() = testApplication {
        setUp()
        val auth = HttpHeaders.Authorization to "Bearer $token"

        val stored = client.put("$API_PREFIX/secrets/github") {
            header(auth.first, auth.second)
            setBody("ghp_example\n")
        }
        assertEquals(HttpStatusCode.NoContent, stored.status)
        assertEquals("ghp_example", secrets.values["github"])

        val listed = authed("$API_PREFIX/secrets")
        assertEquals("""{"names":["github"]}""", listed.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, authed("$API_PREFIX/secrets/github").status)

        val badName = client.put("$API_PREFIX/secrets/Bad%20Name") {
            header(auth.first, auth.second)
            setBody("x")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, badName.status)

        val empty = client.put("$API_PREFIX/secrets/empty") {
            header(auth.first, auth.second)
            setBody("  ")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, empty.status)

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("$API_PREFIX/secrets/github") {
                header(auth.first, auth.second)
            }.status
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("$API_PREFIX/secrets/github") {
                header(auth.first, auth.second)
            }.status
        )
        assertEquals(HttpStatusCode.Unauthorized, client.get("$API_PREFIX/secrets").status)
    }

    /** Plain JSON serializer for the test DataStore (the production one is internal to core:data). */
    private object ArchiveSerializer : androidx.datastore.core.Serializer<ConfigArchive> {
        override val defaultValue = ConfigArchive()

        override suspend fun readFrom(input: InputStream): ConfigArchive =
            Json.decodeFromString(ConfigArchive.serializer(), input.readBytes().decodeToString())

        override suspend fun writeTo(t: ConfigArchive, output: OutputStream) =
            output.write(Json.encodeToString(ConfigArchive.serializer(), t).encodeToByteArray())
    }
}
