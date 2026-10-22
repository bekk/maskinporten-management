package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
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
    fun `a query that is not exactly one scope, or a body that is not UTF-8 JSON, is refused without calling Digdir`() = updateScopeTest {
        val refusals = mapOf(
            "" to validationFailed(FieldError("scope", ValidationCode.MISSING, "scope is required")),
            "?scope=kartverk:a&inactive=true" to malformedRequest("The query must contain only the parameter scope, sent once"),
            "?scope=kartverk:a+kartverk:b" to validationFailed(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace")),
        )
        for ((query, error) in refusals) {
            val response = updateScope(query, """{"description":"a"}""".toByteArray())

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"$query\"")
            assertEquals(error, response.errorResponse(), "for \"$query\"")
        }

        for (contentType in listOf(ContentType.Text.Plain, ContentType("application", ""), ContentType("", "json"))) {
            val response = client.put("/api/scopes?scope=kartverk:a") {
                setBody(ByteArrayContent("""{"description":"a"}""".toByteArray(), contentType))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status, "$contentType")
            assertEquals(
                malformedRequest("The body must be JSON, sent with Content-Type application/json"),
                response.errorResponse(),
                "$contentType",
            )
        }

        val response = updateScope("?scope=kartverk:a", byteArrayOf(0x7B, 0xFF.toByte(), 0x7D))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(malformedRequest("The body must be UTF-8"), response.errorResponse())
        assertTrue(sent.isEmpty())
    }
}
