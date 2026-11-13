package no.kartverket.maskinportenmanagement.restserver.filtering

import no.kartverket.maskinportenmanagement.client.Scope
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeFilterTest {

    private val filter = ScopeFilter(
        exact = listOf("kartverk:nrl.rapportering"),
        prefix = listOf("kartverk:tilgangsstyring/", "kartverk:dokument:"),
    )

    @Test
    fun `allows a scope in exact`() {
        assertTrue(filter.allows(Scope.of("kartverk:nrl.rapportering")))
    }

    @Test
    fun `allows a scope that starts with a prefix`() {
        assertTrue(filter.allows(Scope.of("kartverk:tilgangsstyring/demo.read")))
        assertTrue(filter.allows(Scope.of("kartverk:dokument:bestilling")))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["kartverk:nrl.rapportering.ny", "kartverk:dokumentbestilling", "kartverk:tilgangsstyring"])
    fun `allows nothing else`(scope: String) {
        assertFalse(filter.allows(Scope.of(scope)))
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = ["", " "])
    fun `refuses a blank prefix, which would allow every scope`(prefix: String) {
        val e = assertFailsWith<IllegalArgumentException> { ScopeFilter(emptyList(), listOf(prefix)) }

        assertContains(e.message!!, "must not be blank")
    }

    @Test
    fun `allows nothing when both lists are empty`() {
        assertFalse(ScopeFilter(emptyList(), emptyList()).allows(Scope.of("kartverk:nrl.rapportering")))
    }
}
