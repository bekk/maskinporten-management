package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScopeAccessChangelog
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
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
        val onlyThese = "The query must contain only the parameters scope, page and size, each sent once"
        val refusals = mapOf(
            "" to "Query parameter scope is required",
            "?page=0" to "Query parameter scope is required",
            "?scope=kartverk:a&scope=kartverk:b" to onlyThese,
            "?scope=kartverk:a&page=0&page=1" to onlyThese,
            "?scope=kartverk:a&sort=created" to onlyThese,
            "?scope=kartverk:a+kartverk:b" to "Query parameter scope must not contain whitespace",
        )
        for ((query, error) in refusals) {
            val response = client.get("/api/scopeaccess/changelog$query")

            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals(ErrorResponse(error, ErrorCode.INVALID_REQUEST), response.errorResponse(), query)
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a page below 0 or a size below 1 is refused without calling Digdir`() = changelogTest {
        val refusals = mapOf(
            "page=-1" to "Query parameter page must be a whole number of 0 or more",
            "page=" to "Query parameter page must be a whole number of 0 or more",
            "page=first" to "Query parameter page must be a whole number of 0 or more",
            "size=0" to "Query parameter size must be a whole number of 1 or more",
            "size=1.5" to "Query parameter size must be a whole number of 1 or more",
            "size=99999999999" to "Query parameter size must be a whole number of 1 or more",
        )
        for ((paging, error) in refusals) {
            val response = client.get("/api/scopeaccess/changelog?scope=kartverk:a&$paging")

            assertEquals(HttpStatusCode.BadRequest, response.status, paging)
            assertEquals(ErrorResponse(error, ErrorCode.INVALID_REQUEST), response.errorResponse(), paging)
        }
        assertTrue(sent.isEmpty())
    }
}
