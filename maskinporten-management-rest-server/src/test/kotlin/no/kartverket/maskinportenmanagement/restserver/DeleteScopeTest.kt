package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteScopeTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun deleteScopeTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScope("kartverk:tilgangsstyring/demo.read")) }, filtering = null, block)

    @Test
    fun `sends the scope to Digdir`() {
        for (query in listOf("?scope=kartverk:tilgangsstyring/demo.read", "?scope=kartverk%3Atilgangsstyring%2Fdemo.read")) {
            sent.clear()
            deleteScopeTest {
                assertEquals(HttpStatusCode.OK, client.delete("/api/scopes$query").status, "for $query")
            }

            val request = sent.single()
            assertEquals("DELETE", request.method.name)
            assertEquals("/api/v1/scopes", request.url.path)
            assertEquals("scope=kartverk%3Atilgangsstyring%2Fdemo.read", request.url.rawQuery, "for $query")
        }
    }

    @Test
    fun `a query that is not exactly one scope without whitespace is refused without calling Digdir`() = deleteScopeTest {
        val refusals = mapOf(
            "" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            "?scope=kartverk:a&inactive=true" to FieldError("inactive", ValidationCode.NOT_ALLOWED, "inactive is not allowed"),
            "?scope=kartverk:a+kartverk:b" to FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace"),
        )
        for ((query, fieldError) in refusals) {
            val response = client.delete("/api/scopes$query")

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"$query\"")
            assertEquals(validationFailed(fieldError), response.errorResponse(), "for \"$query\"")
        }
        assertTrue(sent.isEmpty())
    }
}
