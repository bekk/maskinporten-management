package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnlyQueryParameterTest {

    private fun malformed(query: String) =
        assertFailsWith<MalformedRequestException>(query.take(60)) { onlyQueryParameter(query, "scope") }.message

    private fun invalid(query: String) =
        assertFailsWith<InvalidRequestException>(query) { onlyQueryParameter(query, "scope") }.errors

    @Test
    fun `returns the decoded value of the one parameter`() {
        assertEquals("kartverk:matrikkel.read", onlyQueryParameter("scope=kartverk:matrikkel.read", "scope"))
        assertEquals("kartverk:matrikkel.read", onlyQueryParameter("scope=kartverk%3Amatrikkel.read", "scope"))
        assertEquals("a b", onlyQueryParameter("scope=a+b", "scope"))
    }

    @Test
    fun `refuses any query that another parser could read as a different scope`() {
        val padding = List(1023) { "a" }.joinToString("&")
        val queries = listOf(
            "=scope=kartverk:annet",
            "=scope=kartverk:annet&$padding&scope=kartverk:matrikkel.read",
            "scope=kartverk:matrikkel.read&scope=kartverk:annet",
            "scope=kartverk:matrikkel.read&",
            "&scope=kartverk:matrikkel.read",
            "sc%6Fpe=kartverk:matrikkel.read",
            "Scope=kartverk:matrikkel.read",
            "scope",
        )
        for (query in queries) {
            assertEquals("The query must contain only the parameter scope, sent once", malformed(query), query.take(60))
        }
    }

    @Test
    fun `refuses a missing or blank value`() {
        for (query in listOf("", "scope=", "scope=%20", "scope=+")) {
            assertEquals(listOf(FieldError("scope", ValidationCode.MISSING, "scope is required")), invalid(query), query)
        }
    }

    @Test
    fun `refuses a value that is not URL-encoded correctly`() {
        for (query in listOf("scope=%zz", "scope=kartverk%3", "scope=%")) {
            assertEquals(
                listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope is not URL-encoded correctly")),
                invalid(query),
                query,
            )
        }
    }
}
