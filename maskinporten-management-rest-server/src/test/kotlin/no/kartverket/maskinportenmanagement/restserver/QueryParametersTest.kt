package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueryParametersTest {

    private fun read(query: String) = RequestInput().queryParameters(query, "scope", "page", "size")

    private fun refusal(query: String) = assertFailsWith<InvalidRequestException>(query) {
        RequestInput().apply { queryParameters(query, "scope", "page", "size") }.requireValid()
    }.errors

    @Test
    fun `returns the decoded values of the parameters that are there, in any order`() {
        assertEquals(mapOf("scope" to "kartverk:a"), read("scope=kartverk%3Aa"))
        assertEquals(mapOf("scope" to "a b"), read("scope=a+b"))
        assertEquals(mapOf("scope" to "kartverk:a", "page" to "2", "size" to "50"), read("size=50&page=2&scope=kartverk:a"))
    }

    @Test
    fun `refuses a query that cannot be read as names and values, since another parser could read it differently`() {
        for (query in listOf("=scope=kartverk:a", "scope=kartverk:a&", "scope=kartverk:a&&page=0", "scope=kartverk:a&page")) {
            assertEquals(
                "The query must contain only the parameters scope, page and size, each sent once",
                assertFailsWith<MalformedRequestException>(query) { read(query) }.message,
                query,
            )
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
            assertEquals(listOf(error), refusal(query), query)
        }
    }

    @Test
    fun `refuses a value that is not URL-encoded correctly`() {
        assertEquals(
            listOf(FieldError("page", ValidationCode.INVALID_FORMAT, "page is not URL-encoded correctly")),
            refusal("scope=kartverk:a&page=%zz"),
        )
    }

    @Test
    fun `lists every parameter that is wrong, not just the first`() {
        assertEquals(
            listOf(
                FieldError("sort", ValidationCode.NOT_ALLOWED, "sort is not allowed"),
                FieldError("page", ValidationCode.NOT_ALLOWED, "page must be sent once"),
            ),
            refusal("sort=created&page=0&page=1"),
        )
    }
}
