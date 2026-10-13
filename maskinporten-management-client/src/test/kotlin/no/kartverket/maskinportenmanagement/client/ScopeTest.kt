package no.kartverket.maskinportenmanagement.client

import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeTest {

    @Test
    fun `prints as the scope itself`() {
        assertEquals("kartverk:matrikkel.read", Scope.of("kartverk:matrikkel.read").toString())
    }
}
