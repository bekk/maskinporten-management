package no.kartverket.maskinportenmanagement.client

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeTest {

    @Test
    fun `prints as the scope itself`() {
        assertEquals("kartverk:matrikkel.read", Scope.of("kartverk:matrikkel.read").toString())
    }

    @Test
    fun `refuses empty and whitespace`() {
        for (raw in listOf("", " ", "kartverk:a kartverk:b", "kartverk:a\tkartverk:b", " kartverk:a", "kartverk:a\n")) {
            val e = assertFailsWith<IllegalArgumentException>(raw) { Scope.of(raw) }

            assertContains(e.message!!, "whitespace", message = raw)
        }
    }
}
