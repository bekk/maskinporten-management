package no.kartverket.maskinportenmanagement.restserver.scopes

import no.kartverket.maskinportenmanagement.restserver.InvalidRequestException
import no.kartverket.maskinportenmanagement.restserver.MalformedRequestException
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScopeJsonTest {

    private fun refusal(json: String): String =
        assertFailsWith<MalformedRequestException>(json) { ScopeJson.of(json) }.message!!

    private fun fieldErrors(json: String) =
        assertFailsWith<InvalidRequestException>(json) { ScopeJson.of(json) }.errors.map { Triple(it.field, it.code, it.message) }

    @Test
    fun `keeps a scope in Digdir's format exactly as it was written`() {
        val json = """{ "prefix": "kartverk", "subscope": "tilgangsstyring/demo.read", "description": "Lesetilgang, æøå",
            "description#en": "Read access", "long_description#nn": "Lesetilgang", "allowed_integration_types": ["maskinporten"],
            "at_max_age": 120, "visibility": "PRIVATE" }"""

        assertEquals(json, ScopeJson.of(json).value)
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = ["", "[]", "\"kartverk:x\"", "null"])
    fun `refuses JSON that is not an object`(json: String) {
        assertEquals("Expected a scope as a JSON object", refusal(json))
    }

    @Test
    fun `accepts every kind of JSON value`() {
        ScopeJson.of(
            """{"prefix":"kartverk","subscope":"a","active":true,"protected":false,"delegation_source":null,"at_max_age":0,
            "authorization_max_lifetime":-1.5E+3}""",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notStrictJson")
    fun `refuses anything but strict JSON, even inside a list`(json: String) {
        assertTrue(refusal(json).startsWith("The body is not valid JSON: "))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repeatedNames")
    fun `refuses a name given twice, however it is written and wherever it is`(json: String, name: String) {
        val message = refusal(json)
        assertTrue(message.startsWith("The body is not valid JSON: ") && "\"$name\"" in message, message)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["name", "Subscope", "descriptions", "description#", "description#english", "description#de"])
    fun `refuses a field that is not listed, or one spelled differently`(field: String) {
        assertEquals(
            listOf(Triple(field, ValidationCode.NOT_ALLOWED, "$field is not allowed")),
            fieldErrors("""{"prefix":"kartverk","subscope":"a","$field":"x"}"""),
        )
    }

    @Test
    fun `only the scope's own fields count, not those inside a value`() {
        ScopeJson.of(
            """{"prefix":"kartverk","subscope":"a","description":"{\"name\":1,\"name\":2}","allowed_integration_types":["a","a"]}""",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scopesWithoutPrefixOrSubscope")
    fun `refuses a scope without a prefix and a subscope as text`(json: String, error: Triple<String, ValidationCode, String>) {
        assertEquals(listOf(error), fieldErrors(json))
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

    companion object {
        @JvmStatic
        fun notStrictJson(): List<String> =
            listOf("kartverk", "True", "0x10", "-", "01", "+1", "NaN", "1.", "undefined", "'a'", "[maskinporten]")
                .map { """{"prefix":"kartverk","at_max_age":$it}""" } +
                listOf("not json", """{"prefix":""", """{"prefix":"kartverk",}""", """{"prefix":"kartverk"} {}""")

        @JvmStatic
        fun repeatedNames(): List<Arguments> = listOf(
            Arguments.of("""{"subscope":"a","subscope":"b"}""", "subscope"),
            Arguments.of("{\"subscope\":\"a\",\"sub\\u0073cope\":\"b\"}", "subscope"),
            Arguments.of("""{"prefix":"kartverk","subscope":"a","allowed_integration_types":[{"x":1,"x":2}]}""", "x"),
        )

        @JvmStatic
        fun scopesWithoutPrefixOrSubscope(): List<Arguments> = listOf(
            Arguments.of("""{"subscope":"a"}""", Triple("prefix", ValidationCode.MISSING, "prefix is required")),
            Arguments.of("""{"prefix":"kartverk"}""", Triple("subscope", ValidationCode.MISSING, "subscope is required")),
            Arguments.of("""{"prefix":"","subscope":"a"}""", Triple("prefix", ValidationCode.MISSING, "prefix is required")),
            Arguments.of("""{"prefix":"kartverk","subscope":" "}""", Triple("subscope", ValidationCode.MISSING, "subscope is required")),
            Arguments.of("""{"prefix":null,"subscope":"a"}""", Triple("prefix", ValidationCode.MISSING, "prefix is required")),
            Arguments.of("""{"prefix":1,"subscope":"a"}""", Triple("prefix", ValidationCode.INVALID_FORMAT, "prefix must be text")),
            Arguments.of("""{"prefix":"kartverk","subscope":["a"]}""", Triple("subscope", ValidationCode.INVALID_FORMAT, "subscope must be text")),
        )
    }
}
