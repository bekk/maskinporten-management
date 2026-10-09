package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScopesTest {

    private val digdirRequests = mutableListOf<DigdirHttpRequest>()
    private val filteringRequests = mutableListOf<DigdirHttpRequest>()
    private val certificate = "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

    private val scopes = """[
        {"name":"kartverk:tilgangsstyring/demo.read","active":true},
        {"name":"kartverk:tilgangsstyring/demo.write","active":true},
        {"name":"kartverk:dokumentbestilling","active":true},
        {"name":"kartverk:nrl.rapportering","active":true}
    ]"""

    private fun json(status: Int, body: String) = DigdirHttpResponse(status, "application/json", body.toByteArray())

    private fun scopesTest(
        filtering: ((DigdirHttpRequest) -> DigdirHttpResponse)? = {
            json(200, """{"exact":["kartverk:nrl.rapportering"],"prefix":["kartverk:tilgangsstyring/"]}""")
        },
        digdir: (DigdirHttpRequest) -> DigdirHttpResponse = { json(200, scopes) },
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

    private suspend fun ApplicationTestBuilder.listScopes(): HttpResponse =
        client.get("/api/scopes") { header("X-Forwarded-Client-Cert", certificate) }

    private suspend fun HttpResponse.names(): List<String> =
        Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }

    private suspend fun HttpResponse.errorResponse(): ErrorResponse =
        Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())

    @Test
    fun `returns only the scopes the calling app has access to`() = scopesTest {
        val response = listScopes()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            listOf("kartverk:tilgangsstyring/demo.read", "kartverk:tilgangsstyring/demo.write", "kartverk:nrl.rapportering"),
            response.names(),
        )
    }

    @Test
    fun `sends the calling app's certificate header to the filtering service, and asks Digdir for the scopes`() = scopesTest {
        listScopes()

        val filtering = filteringRequests.single()
        assertEquals("POST", filtering.method)
        assertEquals("https://filtering.test/scopes", filtering.url.toString())
        assertEquals(certificate, filtering.headers["X-Forwarded-Client-Cert"])
        val digdir = digdirRequests.single()
        assertEquals("GET", digdir.method)
        assertEquals("https://digdir.test/api/v1/scopes", digdir.url.toString())
    }

    @Test
    fun `an app with access to no scopes gets an empty list`() =
        scopesTest(filtering = { json(200, """{"exact":[],"prefix":[]}""") }) {
            val response = listScopes()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(response.bodyAsText()))
        }

    @Test
    fun `a call without the certificate header is refused before anything else is called`() = scopesTest {
        for (value in listOf(null, "", " ")) {
            val response = client.get("/api/scopes") { if (value != null) header("X-Forwarded-Client-Cert", value) }

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"$value\"")
            assertEquals(
                ErrorResponse("Header X-Forwarded-Client-Cert is required", ErrorCode.INVALID_REQUEST),
                response.errorResponse(),
                "for \"$value\"",
            )
        }
        assertTrue(filteringRequests.isEmpty())
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `gives 502 without calling Digdir when the filtering service fails`() {
        val failures = listOf<(DigdirHttpRequest) -> DigdirHttpResponse>(
            { json(500, "{}") },
            { json(404, "{}") },
            { json(200, "not json") },
            { throw IOException("Connection refused") },
        )
        for (failure in failures) {
            scopesTest(filtering = failure) {
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
            scopesTest(digdir = { json(status, body) }) {
                val response = listScopes()

                assertEquals(status, response.status.value)
                assertContentEquals(body.toByteArray(), response.readRawBytes(), "for $status")
            }
        }
    }

    @Test
    fun `gives 502 when Digdir answers 200 with something other than a list`() =
        scopesTest(digdir = { json(200, """{"name":"kartverk:nrl.rapportering"}""") }) {
            val response = listScopes()

            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals(ErrorResponse("The call to Digdir failed", ErrorCode.UPSTREAM_ERROR), response.errorResponse())
        }

    @Test
    fun `with filtering turned off, returns all of Digdir's scopes and needs no header`() = scopesTest(filtering = null) {
        val response = client.get("/api/scopes")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(scopes.toByteArray(), response.readRawBytes())
    }

    @Test
    fun `filtering is on unless turned off, and then the server does not start without its URL`() {
        val e = assertFailsWith<IllegalStateException> {
            testApplication {
                environment { config = MapApplicationConfig("digdir.baseUrl" to "https://digdir.test") }
                application { configureMaskinportenManagement() }
                startApplication()
            }
        }

        assertContains(e.message!!, "externalFiltering.url")
    }

    @Test
    fun `turned off, filtering needs no URL`() = testApplication {
        environment {
            config = MapApplicationConfig("digdir.baseUrl" to "https://digdir.test", "externalFiltering.enabled" to "false")
        }
        application { configureMaskinportenManagement() }

        startApplication()
    }

    @Test
    fun `the endpoint is in the OpenAPI spec`() = testApplication {
        application {
            configureRouting()
        }

        val paths = Json.parseToJsonElement(client.get("/openapi").bodyAsText()).jsonObject.getValue("paths").jsonObject

        assertEquals(setOf("get"), paths.getValue("/api/scopes").jsonObject.keys)
    }
}
