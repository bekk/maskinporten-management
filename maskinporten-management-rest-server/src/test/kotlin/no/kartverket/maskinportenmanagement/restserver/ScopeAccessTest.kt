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
    fun `sends only the scope to Digdir`() = scopeAccessTest {
        client.get("/api/scopeaccess/orgs?scope=kartverk:matrikkel.read&consumer_orgno=123&inactive=true")

        val request = sent.single()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/scopes/access", request.url.path)
        assertEquals("scope=kartverk%3Amatrikkel.read", request.url.rawQuery)
    }

    @Test
    fun `a missing or blank scope is refused without calling Digdir`() = scopeAccessTest {
        for (query in listOf("", "?scope=", "?scope=%20", "?consumer_orgno=123")) {
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
    fun `a scope sent more than once is refused without calling Digdir`() = scopeAccessTest {
        val response = client.get("/api/scopeaccess/orgs?scope=kartverk:matrikkel.read&scope=kartverk:annet")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            ErrorResponse("Query parameter scope must be sent only once", ErrorCode.INVALID_REQUEST),
            response.errorResponse(),
        )
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
