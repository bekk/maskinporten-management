package no.kartverket.maskinportenmanagement.restserver.scopes

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateScopeTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun updateScopeTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScope("kartverk:tilgangsstyring/demo.read")) }, filtering = null, block)

    private suspend fun ApplicationTestBuilder.updateScope(query: String, body: ByteArray, contentType: ContentType? = ContentType.Application.Json) =
        client.put("/api/scopes$query") {
            if (contentType != null) contentType(contentType)
            setBody(body)
        }

    @Test
    fun `sends the scope in the query and the body byte for byte to Digdir`() {
        val body = digdirScope("kartverk:tilgangsstyring/demo.read").replace("Tilgang til", "Ny tilgang til, æøå")
            .toByteArray(Charsets.UTF_8)
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
            assertContentEquals(body, request.body)
        }
    }

    @Test
    fun `reads the body as UTF-8, whatever charset the Content-Type names`() = updateScopeTest {
        val body = """{"description":"Lesetilgang, æøå"}""".toByteArray(Charsets.UTF_8)

        val response = updateScope("?scope=kartverk:a", body, ContentType.Application.Json.withCharset(Charsets.ISO_8859_1))

        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(body, sent.single().body)
    }

    @Test
    fun `a query without a scope is refused without calling Digdir`() = updateScopeTest {
        val response = updateScope("", """{"description":"a"}""".toByteArray())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(validationFailed(FieldError("scope", ValidationCode.MISSING, "scope is required")), response.errorResponse())
        assertTrue(sent.isEmpty())
    }
}
