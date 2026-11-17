package no.kartverket.maskinportenmanagement.restserver.scopeaccess

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScopeAccessChangelog
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.restserver.errorResponse
import no.kartverket.maskinportenmanagement.restserver.problem.FieldError
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.testServer
import no.kartverket.maskinportenmanagement.restserver.validationFailed
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeAccessChangelogTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun changelogTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScopeAccessChangelog()) }, filtering = null, block)

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = [
            "?scope=kartverk:matrikkel.read | scopeName=kartverk%3Amatrikkel.read",
            "?scope=kartverk%3Amatrikkel.read&page=2 | scopeName=kartverk%3Amatrikkel.read&page=2",
            "?size=50&scope=kartverk:matrikkel.read&page=0 | scopeName=kartverk%3Amatrikkel.read&page=0&size=50",
        ],
    )
    fun `sends the scope to Digdir, and the page and size only when given`(query: String, digdirQuery: String) {
        changelogTest {
            assertEquals(HttpStatusCode.OK, client.get("/api/scopeaccess/changelog$query").status)
        }

        val request = sent.single()
        assertEquals("GET", request.method.name)
        assertEquals("/api/v1/scopes/access/changelog", request.url.path)
        assertEquals(digdirQuery, request.url.rawQuery)
    }

    @Test
    fun `an invalid scope, page and size are all listed, without calling Digdir`() = changelogTest {
        val response = client.get("/api/scopeaccess/changelog?scope=kartverk:a+kartverk:b&page=-1&size=0")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            validationFailed(
                FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace"),
                FieldError("page", ValidationCode.INVALID_FORMAT, "page must be a whole number of 0 or more"),
                FieldError("size", ValidationCode.INVALID_FORMAT, "size must be a whole number of 1 or more"),
            ),
            response.errorResponse(),
        )
        assertTrue(sent.isEmpty())
    }
}
