package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScopeAccessChangelog
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeAccessChangelogTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun changelogTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScopeAccessChangelog()) }, filtering = null, block)

    @Test
    fun `sends the scope to Digdir, and the page and size only when given`() {
        val queries = mapOf(
            "?scope=kartverk:matrikkel.read" to "scopeName=kartverk%3Amatrikkel.read",
            "?scope=kartverk%3Amatrikkel.read&page=2" to "scopeName=kartverk%3Amatrikkel.read&page=2",
            "?size=50&scope=kartverk:matrikkel.read&page=0" to "scopeName=kartverk%3Amatrikkel.read&page=0&size=50",
        )
        for ((query, digdirQuery) in queries) {
            sent.clear()
            changelogTest {
                assertEquals(HttpStatusCode.OK, client.get("/api/scopeaccess/changelog$query").status, "for $query")
            }

            val request = sent.single()
            assertEquals("GET", request.method.name)
            assertEquals("/api/v1/scopes/access/changelog", request.url.path)
            assertEquals(digdirQuery, request.url.rawQuery, "for $query")
        }
    }

    @Test
    fun `a query that is not one scope, with an optional page and size, is refused without calling Digdir`() = changelogTest {
        val refusals = mapOf(
            "" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            "?page=0" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            "?scope=kartverk:a&scope=kartverk:b" to FieldError("scope", ValidationCode.NOT_ALLOWED, "scope must be sent once"),
            "?scope=kartverk:a&page=0&page=1" to FieldError("page", ValidationCode.NOT_ALLOWED, "page must be sent once"),
            "?scope=kartverk:a&sort=created" to FieldError("sort", ValidationCode.NOT_ALLOWED, "sort is not allowed"),
            "?scope=kartverk:a+kartverk:b" to FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace"),
        )
        for ((query, fieldError) in refusals) {
            val response = client.get("/api/scopeaccess/changelog$query")

            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals(validationFailed(fieldError), response.errorResponse(), query)
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a page below 0 or a size below 1 is refused without calling Digdir`() = changelogTest {
        val invalidPage = FieldError("page", ValidationCode.INVALID_FORMAT, "page must be a whole number of 0 or more")
        val invalidSize = FieldError("size", ValidationCode.INVALID_FORMAT, "size must be a whole number of 1 or more")
        val refusals = mapOf(
            "page=-1" to listOf(invalidPage),
            "page=" to listOf(invalidPage),
            "page=first" to listOf(invalidPage),
            "size=0" to listOf(invalidSize),
            "size=1.5" to listOf(invalidSize),
            "size=99999999999" to listOf(invalidSize),
            "page=-1&size=0" to listOf(invalidPage, invalidSize),
        )
        for ((paging, errors) in refusals) {
            val response = client.get("/api/scopeaccess/changelog?scope=kartverk:a&$paging")

            assertEquals(HttpStatusCode.BadRequest, response.status, paging)
            assertEquals(validationFailed(*errors.toTypedArray()), response.errorResponse(), paging)
        }
        assertTrue(sent.isEmpty())
    }
}
