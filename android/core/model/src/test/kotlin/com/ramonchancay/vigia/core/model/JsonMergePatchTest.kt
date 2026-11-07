package com.ramonchancay.vigia.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonMergePatchTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun assertPatch(target: String, patch: String, expected: String) {
        assertEquals(json(expected), JsonMergePatch.apply(json(target), json(patch)))
    }

    // Cases from RFC 7396, appendix A.
    @Test
    fun `rfc 7396 examples`() {
        assertPatch("""{"a":"b"}""", """{"a":"c"}""", """{"a":"c"}""")
        assertPatch("""{"a":"b"}""", """{"b":"c"}""", """{"a":"b","b":"c"}""")
        assertPatch("""{"a":"b"}""", """{"a":null}""", """{}""")
        assertPatch("""{"a":"b","b":"c"}""", """{"a":null}""", """{"b":"c"}""")
        assertPatch("""{"a":["b"]}""", """{"a":"c"}""", """{"a":"c"}""")
        assertPatch("""{"a":"c"}""", """{"a":["b"]}""", """{"a":["b"]}""")
        assertPatch("""{"a":{"b":"c"}}""", """{"a":{"b":"d","c":null}}""", """{"a":{"b":"d"}}""")
        assertPatch("""{"a":[{"b":"c"}]}""", """{"a":[1]}""", """{"a":[1]}""")
        assertPatch("""["a","b"]""", """["c","d"]""", """["c","d"]""")
        assertPatch("""{"a":"b"}""", """["c"]""", """["c"]""")
        assertPatch("""{"a":"foo"}""", "null", "null")
        assertPatch("""{"a":"foo"}""", "\"bar\"", "\"bar\"")
        assertPatch("""{"e":null}""", """{"a":1}""", """{"e":null,"a":1}""")
        assertPatch("""[1,2]""", """{"a":"b","c":null}""", """{"a":"b"}""")
        assertPatch("""{}""", """{"a":{"bb":{"ccc":null}}}""", """{"a":{"bb":{}}}""")
    }

    @Test
    fun `patches a nested panel setting`() {
        assertPatch(
            """{"schemaVersion":1,"theme":{"mode":"dark","palette":"ember"}}""",
            """{"theme":{"palette":"teal"}}""",
            """{"schemaVersion":1,"theme":{"mode":"dark","palette":"teal"}}"""
        )
    }
}
