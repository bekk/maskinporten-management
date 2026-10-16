package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateScopeTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun updateScopeTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScope("kartverk:tilgangsstyring/demo.read")) }, filtering = null, block)

    private suspend fun ApplicationTestBuilder.updateScope(query: String, body: String, contentType: ContentType? = ContentType.Application.Json) =
        client.put("/api/scopes$query") {
            if (contentType != null) contentType(contentType)
            setBody(body)
        }

    @Test
    fun `sends the scope in the query and the body as it is to Digdir`() {
        val body = digdirScope("kartverk:tilgangsstyring/demo.read").replace("Tilgang til", "Ny tilgang til, æøå")
        for (query in listOf("?scope=kartverk:tilgangsstyring/demo.read", "?scope=kartverk%3Atilgangsstyring%2Fdemo.read")) {
            sent.clear()
            updateScopeTest {
                assertEquals(HttpStatusCode.OK, updateScope(query, body).status, "for $query")
            }

            val request = sent.single()
            assertEquals("PUT", request.method.name)
            assertEquals("/api/v1/scopes", request.url.path)
            assertEquals("scope=kartverk%3Atilgangsstyring%2Fdemo.read", request.url.rawQuery, "for $query")
            assertEquals("application/json", request.headers["Content-Type"])
            assertContentEquals(body.toByteArray(Charsets.UTF_8), request.body)
        }
    }

    @Test
    fun `a query that is not exactly one scope, or a body that is not JSON, is refused without calling Digdir`() = updateScopeTest {
        val refusals = mapOf(
            "" to "Query parameter scope is required",
            "?scope=kartverk:a&inactive=true" to "The query must contain only the parameter scope, sent once",
            "?scope=kartverk:a+kartverk:b" to "Query parameter scope must not contain whitespace",
        )
        for ((query, error) in refusals) {
            val response = updateScope(query, """{"description":"a"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"$query\"")
            assertEquals(ErrorResponse(error, ErrorCode.INVALID_REQUEST), response.errorResponse(), "for \"$query\"")
        }

        val response = updateScope("?scope=kartverk:a", """{"description":"a"}""", ContentType.Text.Plain)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            ErrorResponse("The body must be JSON, sent with Content-Type application/json", ErrorCode.INVALID_REQUEST),
            response.errorResponse(),
        )
        assertTrue(sent.isEmpty())
    }
}
