package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.digdirScopeAccess
import no.kartverket.maskinportenmanagement.client.digdirScopeAccessChangelog
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.jsonList
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DigdirAnswersTest {

    private class Endpoint(val method: HttpMethod, val pathAndQuery: String, val digdirSuccess: String, val body: String? = null) {
        override fun toString() = "${method.value} $pathAndQuery"
    }

    private val access = digdirScopeAccess("kartverk:matrikkel.read")

    private val endpoints = listOf(
        Endpoint(HttpMethod.Get, "/api/scopes", jsonList(digdirScope("kartverk:matrikkel.read"))),
        Endpoint(
            HttpMethod.Post,
            "/api/scopes",
            digdirScope("kartverk:matrikkel.read"),
            body = """{"prefix":"kartverk","subscope":"matrikkel.read"}""",
        ),
        Endpoint(
            HttpMethod.Put,
            "/api/scopes?scope=kartverk:matrikkel.read",
            digdirScope("kartverk:matrikkel.read"),
            body = """{"prefix":"kartverk","subscope":"matrikkel.read"}""",
        ),
        Endpoint(HttpMethod.Delete, "/api/scopes?scope=kartverk:matrikkel.read", digdirScope("kartverk:matrikkel.read")),
        Endpoint(HttpMethod.Get, "/api/scopeaccess/orgs?scope=kartverk:matrikkel.read", jsonList(access)),
        Endpoint(HttpMethod.Get, "/api/scopeaccess/scopes?orgnr=311718371", jsonList(access)),
        Endpoint(HttpMethod.Get, "/api/scopeaccess/changelog?scope=kartverk:matrikkel.read", digdirScopeAccessChangelog()),
        Endpoint(HttpMethod.Put, "/api/scopeaccess/311718371?scope=kartverk:matrikkel.read", access),
        Endpoint(HttpMethod.Delete, "/api/scopeaccess/311718371?scope=kartverk:matrikkel.read", access),
    )

    private fun digdirAnswering(answer: (OutgoingRequest) -> OutgoingResponse, block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(
            digdir = OutgoingHttpClient { answer(it) },
            filtering = OutgoingHttpClient { json(200, """{"result":{"exact":[],"prefix":["kartverk:"]}}""") },
            block = block,
        )

    private suspend fun ApplicationTestBuilder.call(endpoint: Endpoint): HttpResponse =
        client.request(endpoint.pathAndQuery) {
            method = endpoint.method
            header("X-Forwarded-Client-Cert", CLIENT_CERTIFICATE)
            if (endpoint.body != null) {
                contentType(ContentType.Application.Json)
                setBody(endpoint.body)
            }
        }

    @Test
    fun `answers with Digdir's data in this API's model`() {
        var answer = json(200, "[]")
        digdirAnswering({ answer }) {
            for (endpoint in endpoints) {
                answer = json(200, endpoint.digdirSuccess)
                val response = call(endpoint)

                assertEquals(HttpStatusCode.OK, response.status, "$endpoint")
                assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters(), "$endpoint")
                assertEquals(Json.parseToJsonElement(endpoint.digdirSuccess), Json.parseToJsonElement(response.bodyAsText()), "$endpoint")
            }
        }
    }

    @Test
    fun `passes Digdir's errors on with their status, content type and body unchanged`() {
        val error = """{"status":404,"error":"ikke funnet: æøå"}""".toByteArray(Charsets.ISO_8859_1)
        val errors = listOf(400, 403, 404, 409, 500, 503).map { OutgoingResponse(it, "application/json;charset=ISO-8859-1", error) }
        var answer = errors.first()
        digdirAnswering({ answer }) {
            for (endpoint in endpoints) {
                for (digdirAnswer in errors) {
                    answer = digdirAnswer
                    val response = call(endpoint)

                    val case = "$endpoint, Digdir answered ${digdirAnswer.statusCode}"
                    assertEquals(digdirAnswer.statusCode, response.status.value, case)
                    assertEquals(digdirAnswer.contentType?.let(ContentType::parse), response.contentType(), case)
                    assertContentEquals(digdirAnswer.body, response.readRawBytes(), case)
                }
            }
        }
    }

    @Test
    fun `leaves out a content type from Digdir that cannot be parsed, and passes on the rest`() =
        digdirAnswering({ OutgoingResponse(503, "text", "Service Unavailable".toByteArray()) }) {
            for (endpoint in endpoints) {
                val response = call(endpoint)

                assertEquals(HttpStatusCode.ServiceUnavailable, response.status, "$endpoint")
                assertNull(response.headers[HttpHeaders.ContentType], "$endpoint")
                assertEquals("Service Unavailable", response.bodyAsText(), "$endpoint")
            }
        }

    @Test
    fun `a 401 from Digdir gives 502, since it means our token is wrong, not the caller`() =
        digdirAnswering({ json(401, """{"error":"invalid_token"}""") }) {
            for (endpoint in endpoints) {
                assertDigdirFailed(call(endpoint), endpoint)
            }
        }

    @Test
    fun `gives 502 when Digdir answers with something this API cannot read`() {
        var answer = json(200, "[]")
        digdirAnswering({ answer }) {
            for (endpoint in endpoints) {
                for (unreadable in listOf(json(200, """{"unexpected":true}"""), OutgoingResponse(204, null, ByteArray(0)))) {
                    answer = unreadable
                    assertDigdirFailed(call(endpoint), endpoint)
                }
            }
        }
    }

    @Test
    fun `gives 502 when Digdir cannot be reached`() =
        digdirAnswering({ throw IOException("Connection refused") }) {
            for (endpoint in endpoints) {
                assertDigdirFailed(call(endpoint), endpoint)
            }
        }

    private suspend fun assertDigdirFailed(response: HttpResponse, endpoint: Endpoint) {
        assertEquals(HttpStatusCode.BadGateway, response.status, "$endpoint")
        assertEquals(upstreamError("The call to Digdir failed"), response.errorResponse(), "$endpoint")
    }
}
