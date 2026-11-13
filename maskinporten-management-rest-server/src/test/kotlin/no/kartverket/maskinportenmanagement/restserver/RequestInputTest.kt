package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.restserver.problem.FieldError
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RequestInputTest {

    private fun read(query: String, vararg names: String) = RequestInput().queryParameters(query, *names)

    private fun errors(read: RequestInput.() -> Unit): List<FieldError> =
        assertFailsWith<InvalidRequestException> { RequestInput().apply(read).requireValid() }.errors

    private fun queryErrors(query: String, vararg names: String) = errors { queryParameters(query, *names) }

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableQueries")
    fun `refuses a query that cannot be read as names and values, since another parser could read it differently`(
        case: String,
        query: String,
    ) {
        assertFailsWith<MalformedRequestException> { read(query, "scope", "page") }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allowedParameters")
    fun `tells which parameters a query that cannot be read may contain`(names: List<String>, message: String) {
        assertEquals(message, assertFailsWith<MalformedRequestException> { read("scope", *names.toTypedArray()) }.message)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedParameters")
    fun `refuses a parameter that is not listed, is spelled differently or is sent twice`(query: String, error: FieldError) {
        assertEquals(listOf(error), queryErrors(query, "scope", "page"))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["scope=%zz", "scope=kartverk%3", "scope=%"])
    fun `refuses a value that is not URL-encoded correctly`(query: String) {
        assertEquals(
            listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope is not URL-encoded correctly")),
            queryErrors(query, "scope"),
        )
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

    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = ["", " "])
    fun `refuses a missing or blank scope`(value: String?) {
        assertEquals(
            listOf(FieldError("scope", ValidationCode.MISSING, "scope is required")),
            errors { scope(Location.QUERY, "scope", value) },
        )
    }

    @Test
    fun `refuses a scope with whitespace`() {
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

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = ["-1", "", "first", "1.5", "99999999999"])
    fun `refuses a whole number below the minimum, or anything else`(value: String) {
        assertEquals(
            listOf(FieldError("page", ValidationCode.INVALID_FORMAT, "page must be a whole number of 0 or more")),
            errors { wholeNumber(Location.QUERY, "page", value, min = 0) },
        )
    }

    @Test
    fun `names the minimum a whole number is below`() {
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

    companion object {
        @JvmStatic
        fun unreadableQueries(): List<Arguments> = listOf(
            Arguments.of("=scope=kartverk:annet", "=scope=kartverk:annet"),
            Arguments.of(
                "=scope=kartverk:annet, then 1023 other parameters",
                "=scope=kartverk:annet&${List(1023) { "a" }.joinToString("&")}&scope=kartverk:matrikkel.read",
            ),
            Arguments.of("scope=kartverk:a&", "scope=kartverk:a&"),
            Arguments.of("&scope=kartverk:a", "&scope=kartverk:a"),
            Arguments.of("scope=kartverk:a&&page=0", "scope=kartverk:a&&page=0"),
            Arguments.of("scope=kartverk:a&page", "scope=kartverk:a&page"),
            Arguments.of("scope", "scope"),
        )

        @JvmStatic
        fun allowedParameters(): List<Arguments> = listOf(
            Arguments.of(listOf<String>(), "The query must be empty"),
            Arguments.of(listOf("scope"), "The query must contain only the parameter scope, sent once"),
            Arguments.of(
                listOf("scope", "page", "size"),
                "The query must contain only the parameters scope, page and size, each sent once",
            ),
        )

        @JvmStatic
        fun refusedParameters(): List<Arguments> = listOf(
            Arguments.of("scope=kartverk:a&page=0&page=1", FieldError("page", ValidationCode.NOT_ALLOWED, "page must be sent once")),
            Arguments.of("scope=kartverk:a&sort=created", FieldError("sort", ValidationCode.NOT_ALLOWED, "sort is not allowed")),
            Arguments.of("scope=kartverk:a&Page=0", FieldError("Page", ValidationCode.NOT_ALLOWED, "Page is not allowed")),
            Arguments.of("scope=kartverk:a&p%61ge=0", FieldError("p%61ge", ValidationCode.NOT_ALLOWED, "p%61ge is not allowed")),
        )
    }
}
