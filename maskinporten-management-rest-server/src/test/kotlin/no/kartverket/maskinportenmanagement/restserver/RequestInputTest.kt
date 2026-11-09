package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.restserver.problem.FieldError
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RequestInputTest {

    private fun read(query: String, vararg names: String) = RequestInput().queryParameters(query, *names)

    private fun errors(case: String = "", read: RequestInput.() -> Unit): List<FieldError> =
        assertFailsWith<InvalidRequestException>(case) { RequestInput().apply(read).requireValid() }.errors

    private fun queryErrors(query: String, vararg names: String) = errors(query.take(60)) { queryParameters(query, *names) }

    @Test
    fun `returns the decoded values of the parameters that are there, in any order`() {
        assertEquals(mapOf("scope" to "kartverk:a"), read("scope=kartverk%3Aa", "scope"))
        assertEquals(mapOf("scope" to "a b"), read("scope=a+b", "scope"))
        assertEquals(
            mapOf("scope" to "kartverk:a", "page" to "2", "size" to "50"),
            read("size=50&page=2&scope=kartverk:a", "scope", "page", "size"),
        )
        assertEquals(emptyMap(), read("", "scope"))
    }

    @Test
    fun `refuses a query that cannot be read as names and values, since another parser could read it differently`() {
        val padding = List(1023) { "a" }.joinToString("&")
        val queries = listOf(
            "=scope=kartverk:annet",
            "=scope=kartverk:annet&$padding&scope=kartverk:matrikkel.read",
            "scope=kartverk:a&",
            "&scope=kartverk:a",
            "scope=kartverk:a&&page=0",
            "scope=kartverk:a&page",
            "scope",
        )
        for (query in queries) {
            assertFailsWith<MalformedRequestException>(query.take(60)) { read(query, "scope", "page") }
        }
    }

    @Test
    fun `tells which parameters a query that cannot be read may contain`() {
        val messages = mapOf(
            arrayOf<String>() to "The query must be empty",
            arrayOf("scope") to "The query must contain only the parameter scope, sent once",
            arrayOf("scope", "page", "size") to "The query must contain only the parameters scope, page and size, each sent once",
        )
        for ((names, message) in messages) {
            assertEquals(message, assertFailsWith<MalformedRequestException> { read("scope", *names) }.message)
        }
    }

    @Test
    fun `refuses a parameter that is not listed, is spelled differently or is sent twice`() {
        val refusals = mapOf(
            "scope=kartverk:a&page=0&page=1" to FieldError("page", ValidationCode.NOT_ALLOWED, "page must be sent once"),
            "scope=kartverk:a&sort=created" to FieldError("sort", ValidationCode.NOT_ALLOWED, "sort is not allowed"),
            "scope=kartverk:a&Page=0" to FieldError("Page", ValidationCode.NOT_ALLOWED, "Page is not allowed"),
            "scope=kartverk:a&p%61ge=0" to FieldError("p%61ge", ValidationCode.NOT_ALLOWED, "p%61ge is not allowed"),
        )
        for ((query, error) in refusals) {
            assertEquals(listOf(error), queryErrors(query, "scope", "page"), query)
        }
    }

    @Test
    fun `refuses a value that is not URL-encoded correctly`() {
        for (query in listOf("scope=%zz", "scope=kartverk%3", "scope=%")) {
            assertEquals(
                listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope is not URL-encoded correctly")),
                queryErrors(query, "scope"),
                query,
            )
        }
    }

    @Test
    fun `lists every parameter that is wrong, not just the first`() {
        assertEquals(
            listOf(
                FieldError("sort", ValidationCode.NOT_ALLOWED, "sort is not allowed"),
                FieldError("page", ValidationCode.NOT_ALLOWED, "page must be sent once"),
            ),
            queryErrors("sort=created&page=0&page=1", "scope", "page"),
        )
    }

    @Test
    fun `reads a scope`() {
        val input = RequestInput()
        val scope by input.scope(Location.QUERY, "scope", "kartverk:matrikkel.read")

        assertEquals(Scope.of("kartverk:matrikkel.read"), scope)
    }

    @Test
    fun `refuses a missing or blank scope, or one with whitespace`() {
        for (value in listOf(null, "", " ")) {
            assertEquals(
                listOf(FieldError("scope", ValidationCode.MISSING, "scope is required")),
                errors("\"$value\"") { scope(Location.QUERY, "scope", value) },
            )
        }
        assertEquals(
            listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace")),
            errors { scope(Location.QUERY, "scope", "kartverk:a kartverk:b") },
        )
    }

    @Test
    fun `reads an organisation number, and refuses one that is missing or not 9 digits`() {
        val input = RequestInput()
        val orgnr by input.organizationNumber(Location.QUERY, "orgnr", "311718371")
        assertEquals(OrganizationNumber.of("311718371"), orgnr)

        assertEquals(
            listOf(FieldError("orgnr", ValidationCode.MISSING, "orgnr is required")),
            errors { organizationNumber(Location.QUERY, "orgnr", null) },
        )
        assertEquals(
            listOf(FieldError("orgnr", ValidationCode.INVALID_FORMAT, "orgnr must be an organisation number of 9 digits")),
            errors { organizationNumber(Location.QUERY, "orgnr", "31171837") },
        )
    }

    @Test
    fun `reads a whole number at or above the minimum, and nothing when it is not sent`() {
        val input = RequestInput()
        val page by input.wholeNumber(Location.QUERY, "page", "0", min = 0)
        val size by input.wholeNumber(Location.QUERY, "size", null, min = 1)

        assertEquals(0, page)
        assertNull(size)
    }

    @Test
    fun `refuses a whole number below the minimum, or anything else`() {
        for (value in listOf("-1", "", "first", "1.5", "99999999999")) {
            assertEquals(
                listOf(FieldError("page", ValidationCode.INVALID_FORMAT, "page must be a whole number of 0 or more")),
                errors(value) { wholeNumber(Location.QUERY, "page", value, min = 0) },
            )
        }
        assertEquals(
            listOf(FieldError("size", ValidationCode.INVALID_FORMAT, "size must be a whole number of 1 or more")),
            errors { wholeNumber(Location.QUERY, "size", "0", min = 1) },
        )
    }

    @Test
    fun `lists a field once per place it is wrong in, so one in the query does not hide one in the body`() {
        assertEquals(
            listOf(
                FieldError("subscope", ValidationCode.NOT_ALLOWED, "subscope is not allowed"),
                FieldError("subscope", ValidationCode.MISSING, "subscope is required"),
            ),
            errors {
                invalid(Location.QUERY, "subscope", ValidationCode.NOT_ALLOWED, "subscope is not allowed")
                invalid(Location.QUERY, "subscope", ValidationCode.INVALID_FORMAT, "subscope is wrong again")
                invalid(Location.BODY, "subscope", ValidationCode.MISSING, "subscope is required")
            },
        )
    }
}
