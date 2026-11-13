package no.kartverket.maskinportenmanagement.restserver.scopes

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.restserver.errorResponse
import no.kartverket.maskinportenmanagement.restserver.problem.FieldError
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.testServer
import no.kartverket.maskinportenmanagement.restserver.validationFailed
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteScopeTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun deleteScopeTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScope("kartverk:tilgangsstyring/demo.read")) }, filtering = null, block)

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["?scope=kartverk:tilgangsstyring/demo.read", "?scope=kartverk%3Atilgangsstyring%2Fdemo.read"])
    fun `sends the scope to Digdir`(query: String) {
        deleteScopeTest {
            assertEquals(HttpStatusCode.OK, client.delete("/api/scopes$query").status)
        }

        val request = sent.single()
        assertEquals("DELETE", request.method.name)
        assertEquals("/api/v1/scopes", request.url.path)
        assertEquals("scope=kartverk%3Atilgangsstyring%2Fdemo.read", request.url.rawQuery)
    }

    @Test
    fun `a query without a scope is refused without calling Digdir`() = deleteScopeTest {
        val response = client.delete("/api/scopes")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(validationFailed(FieldError("scope", ValidationCode.MISSING, "scope is required")), response.errorResponse())
        assertTrue(sent.isEmpty())
    }
}
