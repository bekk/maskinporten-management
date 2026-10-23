package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueryParametersTest {

    private fun read(query: String) = queryParameters(query, "scope", "page", "size")

    private fun malformed(query: String) = assertFailsWith<MalformedRequestException>(query) { read(query) }.message

    private fun invalid(query: String) = assertFailsWith<InvalidRequestException>(query) { read(query) }.errors

    @Test
    fun `returns the decoded values of the parameters that are there, in any order`() {
        assertEquals(mapOf("scope" to "kartverk:a"), read("scope=kartverk%3Aa"))
        assertEquals(mapOf("scope" to "kartverk:a", "page" to "2", "size" to "50"), read("size=50&page=2&scope=kartverk:a"))
    }

    @Test
    fun `refuses any query that another parser could read as different values`() {
        val queries = listOf(
            "scope=kartverk:a&page=0&page=1",
            "scope=kartverk:a&sort=created",
            "=scope=kartverk:a",
            "scope=kartverk:a&",
            "scope=kartverk:a&&page=0",
            "scope=kartverk:a&page",
            "scope=kartverk:a&Page=0",
            "scope=kartverk:a&p%61ge=0",
        )
        for (query in queries) {
            assertEquals("The query must contain only the parameters scope, page and size, each sent once", malformed(query), query)
        }
    }

    @Test
    fun `refuses a query without the required parameter, and a value that is not URL-encoded correctly`() {
        for (query in listOf("", "page=0", "scope=&page=0", "scope=+")) {
            assertEquals(listOf(FieldError("scope", ValidationCode.MISSING, "scope is required")), invalid(query), query)
        }
        assertEquals(
            listOf(FieldError("page", ValidationCode.INVALID_FORMAT, "page is not URL-encoded correctly")),
            invalid("scope=kartverk:a&page=%zz"),
        )
    }
}
