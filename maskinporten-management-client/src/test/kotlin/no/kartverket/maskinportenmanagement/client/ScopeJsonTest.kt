package no.kartverket.maskinportenmanagement.client

import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.client.validation.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScopeJsonTest {

    private fun refusal(json: String): String =
        assertFailsWith<IllegalArgumentException>(json) { ScopeJson.of(json) }.message!!

    private fun fieldErrors(json: String) =
        assertFailsWith<ValidationException>(json) { ScopeJson.of(json) }.errors.map { Triple(it.field, it.code, it.message) }

    @Test
    fun `keeps a scope in Digdir's format exactly as it was written`() {
        val json = """{ "prefix": "kartverk", "subscope": "tilgangsstyring/demo.read", "description": "Lesetilgang, æøå",
            "description#en": "Read access", "long_description#nn": "Lesetilgang", "allowed_integration_types": ["maskinporten"],
            "at_max_age": 120, "visibility": "PRIVATE" }"""

        assertEquals(json, ScopeJson.of(json).value)
    }

    @Test
    fun `refuses JSON that is not an object`() {
        for (json in listOf("", "[]", "\"kartverk:x\"", "null")) {
            assertEquals("Expected a scope as a JSON object", refusal(json), json)
        }
    }

    @Test
    fun `accepts every kind of JSON value`() {
        ScopeJson.of(
            """{"prefix":"kartverk","subscope":"a","active":true,"protected":false,"delegation_source":null,"at_max_age":0,
            "authorization_max_lifetime":-1.5E+3}""",
        )
    }

    @Test
    fun `refuses anything but strict JSON, even inside a list`() {
        val values = listOf("kartverk", "True", "0x10", "-", "01", "+1", "NaN", "1.", "undefined", "'a'", "[maskinporten]")
        val bodies = values.map { """{"prefix":"kartverk","at_max_age":$it}""" } +
            listOf("not json", """{"prefix":""", """{"prefix":"kartverk",}""", """{"prefix":"kartverk"} {}""")
        for (json in bodies) {
            assertTrue(refusal(json).startsWith("The body is not valid JSON: "), json)
        }
    }

    @Test
    fun `refuses a name given twice, however it is written and wherever it is`() {
        val repeated = mapOf(
            """{"subscope":"a","subscope":"b"}""" to "subscope",
            "{\"subscope\":\"a\",\"sub\\u0073cope\":\"b\"}" to "subscope",
            """{"prefix":"kartverk","subscope":"a","allowed_integration_types":[{"x":1,"x":2}]}""" to "x",
        )
        for ((json, name) in repeated) {
            val message = refusal(json)
            assertTrue(message.startsWith("The body is not valid JSON: ") && "\"$name\"" in message, message)
        }
    }

    @Test
    fun `refuses a field that is not listed, or one spelled differently`() {
        for (field in listOf("name", "Subscope", "descriptions", "description#", "description#english", "description#de")) {
            assertEquals(
                listOf(Triple(field, ValidationCode.NOT_ALLOWED, "$field is not allowed")),
                fieldErrors("""{"prefix":"kartverk","subscope":"a","$field":"x"}"""),
                field,
            )
        }
    }

    @Test
    fun `only the scope's own fields count, not those inside a value`() {
        ScopeJson.of(
            """{"prefix":"kartverk","subscope":"a","description":"{\"name\":1,\"name\":2}","allowed_integration_types":["a","a"]}""",
        )
    }

    @Test
    fun `refuses a scope without a prefix and a subscope as text`() {
        val refusals = mapOf(
            """{"subscope":"a"}""" to Triple("prefix", ValidationCode.MISSING, "prefix is required"),
            """{"prefix":"kartverk"}""" to Triple("subscope", ValidationCode.MISSING, "subscope is required"),
            """{"prefix":"","subscope":"a"}""" to Triple("prefix", ValidationCode.MISSING, "prefix is required"),
            """{"prefix":"kartverk","subscope":" "}""" to Triple("subscope", ValidationCode.MISSING, "subscope is required"),
            """{"prefix":null,"subscope":"a"}""" to Triple("prefix", ValidationCode.MISSING, "prefix is required"),
            """{"prefix":1,"subscope":"a"}""" to Triple("prefix", ValidationCode.INVALID_FORMAT, "prefix must be text"),
            """{"prefix":"kartverk","subscope":["a"]}""" to Triple("subscope", ValidationCode.INVALID_FORMAT, "subscope must be text"),
        )
        for ((json, error) in refusals) {
            assertEquals(listOf(error), fieldErrors(json), json)
        }
    }

    @Test
    fun `lists every field that is wrong, not just the first`() {
        assertEquals(
            listOf(
                Triple("name", ValidationCode.NOT_ALLOWED, "name is not allowed"),
                Triple("Subscope", ValidationCode.NOT_ALLOWED, "Subscope is not allowed"),
                Triple("prefix", ValidationCode.MISSING, "prefix is required"),
                Triple("subscope", ValidationCode.MISSING, "subscope is required"),
            ),
            fieldErrors("""{"name":"x","Subscope":"a"}"""),
        )
    }
}
