package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
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

class ScopeAccessScopesTest {

    private val digdirRequests = mutableListOf<DigdirHttpRequest>()
    private val filteringRequests = mutableListOf<DigdirHttpRequest>()
    private val certificate = "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

    private val scopeAccess = """[
        {"scope":"kartverk:tilgangsstyring/demo.read","consumer_orgno":"311718371","state":"APPROVED"},
        {"scope":"kartverk:tilgangsstyring/demo.write","consumer_orgno":"311718371","state":"DENIED"},
        {"scope":"kartverk:dokumentbestilling","consumer_orgno":"311718371","state":"APPROVED"},
        {"scope":"kartverk:nrl.rapportering","consumer_orgno":"311718371","state":"APPROVED"}
    ]"""

    private fun json(status: Int, body: String) = DigdirHttpResponse(status, "application/json", body.toByteArray())

    private fun scopeAccessTest(
        filtering: ((DigdirHttpRequest) -> DigdirHttpResponse)? = {
            json(200, """{"exact":["kartverk:nrl.rapportering"],"prefix":["kartverk:tilgangsstyring/"]}""")
        },
        digdir: (DigdirHttpRequest) -> DigdirHttpResponse = { json(200, scopeAccess) },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val externalFiltering = filtering?.let { answer ->
            ExternalFilteringClient(
                "https://filtering.test/scopes",
                DigdirHttpClient { request ->
                    filteringRequests += request
                    answer(request)
                },
            )
        }
        application {
            configureSerialization()
            configureErrorHandling()
            configureMaskinportenManagement(
                MaskinportenManagementClient(
                    "https://digdir.test",
                    DigdirHttpClient { request ->
                        digdirRequests += request
                        digdir(request)
                    },
                ),
                ExternalFiltering(externalFiltering),
            )
            configureRouting()
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.listScopes(query: String = "?orgnr=311718371"): HttpResponse =
        client.get("/api/scopeaccess/scopes$query") { header("X-Forwarded-Client-Cert", certificate) }

    private suspend fun HttpResponse.scopes(): List<String> =
        Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject.getValue("scope").jsonPrimitive.content }

    private suspend fun HttpResponse.errorResponse(): ErrorResponse =
        Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())

    @Test
    fun `returns only the organisation's access to the scopes the calling app has access to`() = scopeAccessTest {
        val response = listScopes()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            listOf("kartverk:tilgangsstyring/demo.read", "kartverk:tilgangsstyring/demo.write", "kartverk:nrl.rapportering"),
            response.scopes(),
        )
    }

    @Test
    fun `sends the calling app's certificate header to the filtering service, and asks Digdir for the organisation's access`() =
        scopeAccessTest {
            listScopes()

            val filtering = filteringRequests.single()
            assertEquals("POST", filtering.method)
            assertEquals(certificate, filtering.headers["X-Forwarded-Client-Cert"])
            val digdir = digdirRequests.single()
            assertEquals("GET", digdir.method)
            assertEquals("https://digdir.test/api/v1/scopes/access?consumer_orgno=311718371", digdir.url.toString())
        }

    @Test
    fun `a query that is not exactly one orgnr of 9 digits is refused before anything else is called`() = scopeAccessTest {
        val queries = listOf(
            "",
            "?orgnr=",
            "?orgnr=31171837",
            "?orgnr=3117183710",
            "?orgnr=31171837a",
            "?orgnr=%20311718371",
            "?orgnr=311718371&orgnr=311718371",
            "?orgnr=311718371&scope=kartverk:x",
            "?consumer_orgno=311718371",
        )
        for (query in queries) {
            val response = listScopes(query)

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"$query\"")
            assertEquals(ErrorCode.INVALID_REQUEST, response.errorResponse().code, "for \"$query\"")
        }
        assertEquals(
            ErrorResponse("Query parameter orgnr must be an organisation number of 9 digits", ErrorCode.INVALID_REQUEST),
            listScopes("?orgnr=31171837").errorResponse(),
        )
        assertTrue(filteringRequests.isEmpty())
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `a call without the certificate header is refused before anything else is called`() = scopeAccessTest {
        val response = client.get("/api/scopeaccess/scopes?orgnr=311718371")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            ErrorResponse("Header X-Forwarded-Client-Cert is required", ErrorCode.INVALID_REQUEST),
            response.errorResponse(),
        )
        assertTrue(filteringRequests.isEmpty())
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `gives 502 without calling Digdir when the filtering service fails`() {
        val failures = listOf<(DigdirHttpRequest) -> DigdirHttpResponse>(
            { json(500, "{}") },
            { throw IOException("Connection refused") },
        )
        for (failure in failures) {
            scopeAccessTest(filtering = failure) {
                val response = listScopes()

                assertEquals(HttpStatusCode.BadGateway, response.status)
                assertEquals(
                    ErrorResponse("The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR),
                    response.errorResponse(),
                )
            }
        }
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `passes Digdir's errors on unchanged`() {
        val body = """{"status":403,"error":"ingen tilgang"}"""
        for (status in listOf(400, 401, 403, 500, 503)) {
            scopeAccessTest(digdir = { json(status, body) }) {
                val response = listScopes()

                assertEquals(status, response.status.value)
                assertContentEquals(body.toByteArray(), response.readRawBytes(), "for $status")
            }
        }
    }

    @Test
    fun `with filtering turned off, returns all of Digdir's answer and needs no header`() = scopeAccessTest(filtering = null) {
        val response = client.get("/api/scopeaccess/scopes?orgnr=311718371")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(scopeAccess.toByteArray(), response.readRawBytes())
    }
}
