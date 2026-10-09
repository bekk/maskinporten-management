package no.kartverket.maskinportenmanagement.client.filtering

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeFilterTest {

    private val filter = ScopeFilter(
        exact = listOf("kartverk:nrl.rapportering"),
        prefix = listOf("kartverk:tilgangsstyring/", "kartverk:dokument:"),
    )

    @Test
    fun `allows a scope in exact`() {
        assertTrue(filter.allows("kartverk:nrl.rapportering"))
    }

    @Test
    fun `allows a scope that starts with a prefix`() {
        assertTrue(filter.allows("kartverk:tilgangsstyring/demo.read"))
        assertTrue(filter.allows("kartverk:dokument:bestilling"))
    }

    @Test
    fun `allows nothing else`() {
        for (scope in listOf("kartverk:nrl.rapportering.ny", "kartverk:dokumentbestilling", "kartverk:tilgangsstyring", "")) {
            assertFalse(filter.allows(scope), scope)
        }
    }

    @Test
    fun `allows nothing when both lists are empty`() {
        assertFalse(ScopeFilter(emptyList(), emptyList()).allows("kartverk:nrl.rapportering"))
    }
}
