package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.ProblemType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateScopeTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun createScopeTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScope("kartverk:tilgangsstyring/demo.read")) }, filtering = null, block)

    private suspend fun ApplicationTestBuilder.createScope(body: ByteArray, contentType: ContentType? = ContentType.Application.Json) =
        client.post("/api/scopes") {
            if (contentType != null) contentType(contentType)
            setBody(body)
        }

    @Test
    fun `sends the scope to Digdir byte for byte`() = createScopeTest {
        val body = """{ "prefix": "kartverk",
            "subscope": "tilgangsstyring/demo.read", "description": "Lesetilgang, æøå" }""".toByteArray(Charsets.UTF_8)

        assertEquals(HttpStatusCode.OK, createScope(body).status)

        val request = sent.single()
        assertEquals("POST", request.method.name)
        assertEquals("https://digdir.test/api/v1/scopes", request.url.toString())
        assertEquals("application/json", request.headers["Content-Type"])
        assertContentEquals(body, request.body)
    }

    @Test
    fun `a body that is not a JSON object is refused without calling Digdir`() = createScopeTest {
        val refusals = mapOf(
            "[]" to "Expected a scope as a JSON object",
            """{"prefix":"kartverk","active":True}""" to "The body is not valid JSON: ",
            """{"subscope":"a","subscope":"b"}""" to "The body is not valid JSON: ",
        )
        for ((body, detail) in refusals) {
            val response = createScope(body.toByteArray())

            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            val answer = response.errorResponse()
            assertEquals(ProblemType.MALFORMED_REQUEST, answer.type, body)
            assertTrue(answer.detail.orEmpty().startsWith(detail), "$body: ${answer.detail}")
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `every field that is wrong in the body is listed, without calling Digdir`() = createScopeTest {
        val response = createScope("""{"prefix":"kartverk","Subscope":"a"}""".toByteArray())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            validationFailed(
                FieldError("Subscope", ValidationCode.NOT_ALLOWED, "Subscope is not allowed"),
                FieldError("subscope", ValidationCode.MISSING, "subscope is required"),
            ),
            response.errorResponse(),
        )
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a URL with a query is refused without calling Digdir, so the scope cannot be read from it instead`() = createScopeTest {
        for (query in listOf("?scope=kartverk:matrikkel.read", "?subscope=matrikkel.read")) {
            val response = client.post("/api/scopes$query") {
                contentType(ContentType.Application.Json)
                setBody("""{"prefix":"kartverk","subscope":"tilgangsstyring/demo.read"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals(malformedRequest("The query must be empty"), response.errorResponse(), query)
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a body that is not UTF-8 JSON is refused without calling Digdir`() = createScopeTest {
        val scope = """{"prefix":"kartverk"}""".toByteArray()
        val refusals = listOf(
            Triple(scope, null, "The body must be JSON, sent with Content-Type application/json"),
            Triple(scope, ContentType.Text.Plain, "The body must be JSON, sent with Content-Type application/json"),
            Triple(byteArrayOf(0x7B, 0xFF.toByte(), 0x7D), ContentType.Application.Json, "The body must be UTF-8"),
        )
        for ((body, contentType, error) in refusals) {
            val response = createScope(body, contentType)

            assertEquals(HttpStatusCode.BadRequest, response.status, "$contentType")
            assertEquals(malformedRequest(error), response.errorResponse(), "$contentType")
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a Content-Type that cannot be read is refused without calling Digdir`() = createScopeTest {
        for (contentType in listOf(ContentType("application", ""), ContentType("", "json"))) {
            val response = client.post("/api/scopes") {
                setBody(ByteArrayContent("""{"prefix":"kartverk"}""".toByteArray(), contentType))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status, "$contentType")
            assertEquals(
                malformedRequest("The body must be JSON, sent with Content-Type application/json"),
                response.errorResponse(),
                "$contentType",
            )
        }
        assertTrue(sent.isEmpty())
    }
}
