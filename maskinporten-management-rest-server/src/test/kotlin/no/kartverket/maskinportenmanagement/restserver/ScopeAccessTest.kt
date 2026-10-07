package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpRequest
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeAccessTest {

    private val sent = mutableListOf<DigdirHttpRequest>()

    private fun scopeAccessTest(
        answer: (DigdirHttpRequest) -> DigdirHttpResponse = { DigdirHttpResponse(200, "application/json", "[]".toByteArray()) },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val digdir = DigdirHttpClient { request ->
            sent += request
            answer(request)
        }
        application {
            configureSerialization()
            configureErrorHandling()
            configureMaskinportenManagement(MaskinportenManagementClient("https://digdir.test", digdir))
            configureRouting()
        }
        block()
    }

    private suspend fun HttpResponse.errorResponse(): ErrorResponse =
        Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())

    @Test
    fun `returns Digdir's response unchanged`() {
        val body = """[{"scope":"kartverk:matrikkel.read","consumer_orgno":"311718371","state":"APPROVED"}]"""
            .toByteArray()
        scopeAccessTest(answer = { DigdirHttpResponse(200, "application/json", body) }) {
            val response = client.get("/api/scopeaccess/orgs?scope=kartverk:matrikkel.read")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("application/json", response.headers[HttpHeaders.ContentType])
            assertContentEquals(body, response.readRawBytes())
        }
    }

    @Test
    fun `passes Digdir's error responses on unchanged`() {
        val body = """{"status":404,"error":"ikke funnet: æøå"}""".toByteArray(Charsets.ISO_8859_1)
        for (status in listOf(400, 401, 403, 404, 500, 503)) {
            scopeAccessTest(answer = { DigdirHttpResponse(status, "application/json;charset=ISO-8859-1", body) }) {
                val response = client.get("/api/scopeaccess/orgs?scope=kartverk:matrikkel.read")

                assertEquals(status, response.status.value)
                assertEquals(ContentType.parse("application/json;charset=ISO-8859-1"), response.contentType(), "for $status")
                assertContentEquals(body, response.readRawBytes(), "for $status")
            }
        }
    }

    @Test
    fun `a content type from Digdir that cannot be parsed is left out, and the rest passed on`() {
        val body = "Service Unavailable".toByteArray()
        scopeAccessTest(answer = { DigdirHttpResponse(503, "text", body) }) {
            val response = client.get("/api/scopeaccess/orgs?scope=kartverk:matrikkel.read")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertContentEquals(body, response.readRawBytes())
        }
    }

    @Test
    fun `sends the scope to Digdir`() {
        for (query in listOf("?scope=kartverk:matrikkel.read", "?scope=kartverk%3Amatrikkel.read")) {
            sent.clear()
            scopeAccessTest {
                assertEquals(HttpStatusCode.OK, client.get("/api/scopeaccess/orgs$query").status, "for $query")
            }

            val request = sent.single()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/scopes/access", request.url.path)
            assertEquals("scope=kartverk%3Amatrikkel.read", request.url.rawQuery, "for $query")
        }
    }

    @Test
    fun `a missing or blank scope is refused without calling Digdir`() = scopeAccessTest {
        for (query in listOf("", "?scope=", "?scope=%20", "?scope=+")) {
            val response = client.get("/api/scopeaccess/orgs$query")

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"$query\"")
            assertEquals(
                ErrorResponse("Query parameter scope is required", ErrorCode.INVALID_REQUEST),
                response.errorResponse(),
                "for \"$query\"",
            )
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a query that is not exactly one scope is refused without calling Digdir`() = scopeAccessTest {
        val queries = listOf(
            "?scope=kartverk:matrikkel.read&scope=kartverk:annet",
            "?scope=kartverk:matrikkel.read&consumer_orgno=123",
            "?consumer_orgno=123",
            "?Scope=kartverk:matrikkel.read",
        )
        for (query in queries) {
            val response = client.get("/api/scopeaccess/orgs$query")

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"${query.take(60)}\"")
            assertEquals(
                ErrorResponse("The query must contain only the parameter scope, sent once", ErrorCode.INVALID_REQUEST),
                response.errorResponse(),
                "for \"${query.take(60)}\"",
            )
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a call Digdir never answers gives 502`() = scopeAccessTest(answer = { throw IOException("Connection refused") }) {
        val response = client.get("/api/scopeaccess/orgs?scope=kartverk:matrikkel.read")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ErrorResponse("The call to Digdir failed", ErrorCode.UPSTREAM_ERROR), response.errorResponse())
    }

    @Test
    fun `the endpoint is in the OpenAPI spec`() = testApplication {
        application {
            configureRouting()
        }

        val spec = Json.parseToJsonElement(client.get("/openapi").bodyAsText()).jsonObject

        assertTrue(spec.getValue("paths").jsonObject.containsKey("/api/scopeaccess/orgs"))
    }
}
