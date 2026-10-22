package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeQueryParameterTest {

    private fun read(query: String): Scope {
        val input = RequestInput()
        val scope by input.scope("scope", input.queryParameters(query, "scope")["scope"])
        input.requireValid()
        return scope
    }

    private fun refusal(query: String) = assertFailsWith<InvalidRequestException>(query.take(60)) { read(query) }.errors

    @Test
    fun `returns the decoded scope`() {
        assertEquals(Scope.of("kartverk:matrikkel.read"), read("scope=kartverk:matrikkel.read"))
        assertEquals(Scope.of("kartverk:matrikkel.read"), read("scope=kartverk%3Amatrikkel.read"))
    }

    @Test
    fun `refuses a query that cannot be read as names and values, since another parser could read it as a different scope`() {
        val padding = List(1023) { "a" }.joinToString("&")
        val queries = listOf(
            "=scope=kartverk:annet",
            "=scope=kartverk:annet&$padding&scope=kartverk:matrikkel.read",
            "scope=kartverk:matrikkel.read&",
            "&scope=kartverk:matrikkel.read",
            "scope",
        )
        for (query in queries) {
            assertEquals(
                "The query must contain only the parameter scope, sent once",
                assertFailsWith<MalformedRequestException>(query.take(60)) { read(query) }.message,
                query.take(60),
            )
        }
    }

    @Test
    fun `refuses a scope sent twice, or a parameter spelled differently`() {
        val missing = FieldError("scope", ValidationCode.MISSING, "scope is required")
        val refusals = mapOf(
            "scope=kartverk:matrikkel.read&scope=kartverk:annet" to
                listOf(FieldError("scope", ValidationCode.NOT_ALLOWED, "scope must be sent once")),
            "sc%6Fpe=kartverk:matrikkel.read" to
                listOf(FieldError("sc%6Fpe", ValidationCode.NOT_ALLOWED, "sc%6Fpe is not allowed"), missing),
            "Scope=kartverk:matrikkel.read" to
                listOf(FieldError("Scope", ValidationCode.NOT_ALLOWED, "Scope is not allowed"), missing),
        )
        for ((query, errors) in refusals) {
            assertEquals(errors, refusal(query), query)
        }
    }

    @Test
    fun `refuses a missing or blank value`() {
        for (query in listOf("", "scope=", "scope=%20", "scope=+")) {
            assertEquals(listOf(FieldError("scope", ValidationCode.MISSING, "scope is required")), refusal(query), query)
        }
    }

    @Test
    fun `refuses a value that is not URL-encoded correctly, or contains whitespace`() {
        for (query in listOf("scope=%zz", "scope=kartverk%3", "scope=%")) {
            assertEquals(
                listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope is not URL-encoded correctly")),
                refusal(query),
                query,
            )
        }
        assertEquals(
            listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace")),
            refusal("scope=kartverk:a+kartverk:b"),
        )
    }
}
