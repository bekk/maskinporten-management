package no.kartverket.maskinportenmanagement.client.validation

import no.kartverket.maskinportenmanagement.client.ScopeName
import no.kartverket.maskinportenmanagement.client.exception.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeAccessValidationTest {

    @Test
    fun `accepts a prefix and a subscope separated by a colon`() {
        for (scope in listOf("kartverk:matrikkel.read", "kartverk:a", "kartverk:eiendom/v2.write")) {
            assertEquals(emptyList(), ScopeAccessValidation.validate(scope), scope)
            assertEquals(scope, ScopeName.parse(scope).value)
        }
    }

    @Test
    fun `a missing or blank scope is MISSING`() {
        for (scope in listOf(null, "", "   ")) {
            assertEquals(
                listOf(ValidationError("scope", ValidationCode.MISSING, "scope is required")),
                ScopeAccessValidation.validate(scope),
                "for \"$scope\"",
            )
        }
    }

    @Test
    fun `a scope without both a prefix and a subscope is INVALID_FORMAT`() {
        for (scope in listOf("kartverk", ":matrikkel", "kartverk:", "kart verk:matrikkel", "kartverk:matrikkel read")) {
            assertEquals(
                ValidationCode.INVALID_FORMAT,
                ScopeAccessValidation.validate(scope).single().code,
                "for \"$scope\"",
            )
        }
    }

    @Test
    fun `parse throws the same error validate reports`() {
        val e = assertFailsWith<ValidationException> { ScopeName.parse("kartverk") }

        assertEquals(ScopeAccessValidation.validate("kartverk"), e.errors)
    }
}
