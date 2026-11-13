package no.kartverket.maskinportenmanagement.client

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeTest {

    @Test
    fun `prints as the scope itself`() {
        assertEquals("kartverk:matrikkel.read", Scope.of("kartverk:matrikkel.read").toString())
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = ["", " ", "kartverk:a kartverk:b", "kartverk:a\tkartverk:b", " kartverk:a", "kartverk:a\n"])
    fun `refuses empty and whitespace`(raw: String) {
        val e = assertFailsWith<IllegalArgumentException> { Scope.of(raw) }

        assertContains(e.message!!, "whitespace")
    }
}
