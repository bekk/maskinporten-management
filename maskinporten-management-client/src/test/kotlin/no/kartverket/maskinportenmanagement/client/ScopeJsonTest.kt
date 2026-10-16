package no.kartverket.maskinportenmanagement.client

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeJsonTest {

    private fun refusal(json: String): String =
        assertFailsWith<IllegalArgumentException>(json) { ScopeJson.of(json) }.message!!

    @Test
    fun `keeps a scope in Digdir's format exactly as it was written`() {
        val json = """{ "prefix": "kartverk", "subscope": "tilgangsstyring/demo.read", "description": "Lesetilgang, æøå",
            "description#en": "Read access", "long_description#nn": "Lesetilgang", "allowed_integration_types": ["maskinporten"],
            "at_max_age": 120, "visibility": "PRIVATE" }"""

        assertEquals(json, ScopeJson.of(json).value)
    }

    @Test
    fun `refuses anything but a JSON object`() {
        for (json in listOf("", "not json", "[]", "\"kartverk:x\"", "{\"prefix\":")) {
            assertEquals("Expected a scope as a JSON object", refusal(json), json)
        }
    }

    @Test
    fun `refuses a field given twice, however it is written`() {
        for (json in listOf("""{"subscope":"a","subscope":"b"}""", """{"subscope":"a","subscope":"b"}""")) {
            assertEquals("The field \"subscope\" is given more than once", refusal(json), json)
        }
    }

    @Test
    fun `refuses a field Digdir does not have, or one spelled differently`() {
        for (field in listOf("name", "Subscope", "descriptions", "description#", "description#english")) {
            assertContains(refusal("""{"prefix":"kartverk","$field":"x"}"""), "Digdir has no field \"$field\"", message = field)
        }
    }

    @Test
    fun `only the scope's own fields count, not those inside a value`() {
        ScopeJson.of("""{"prefix":"kartverk","description":"{\"name\":1,\"name\":2}","allowed_integration_types":["a","a"]}""")
    }
}
