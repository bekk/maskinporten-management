package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.jsonList
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopesTest {

    private val digdirRequests = mutableListOf<OutgoingRequest>()
    private val filteringRequests = mutableListOf<OutgoingRequest>()

    private val scopes = jsonList(
        digdirScope("kartverk:tilgangsstyring/demo.read"),
        digdirScope("kartverk:tilgangsstyring/demo.write"),
        digdirScope("kartverk:dokumentbestilling"),
        digdirScope("kartverk:nrl.rapportering"),
    )

    private fun scopesTest(
        filtering: ((OutgoingRequest) -> OutgoingResponse)? = {
            json(200, """{"result":{"exact":["kartverk:nrl.rapportering"],"prefix":["kartverk:tilgangsstyring/"]}}""")
        },
        digdir: (OutgoingRequest) -> OutgoingResponse = { json(200, scopes) },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testServer(recording(digdirRequests, digdir), filtering?.let { recording(filteringRequests, it) }, block)

    private suspend fun ApplicationTestBuilder.listScopes(): HttpResponse =
        client.get("/api/scopes") { header("X-Forwarded-Client-Cert", CLIENT_CERTIFICATE) }

    private suspend fun HttpResponse.names(): List<String> =
        Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }

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
    fun `sends the calling app's certificate as OPA input to the filtering service, and asks Digdir for the scopes`() = scopesTest {
        listScopes()

        assertEquals(CLIENT_CERTIFICATE, filteringRequests.single().certificateSentToFiltering())
        assertEquals("https://digdir.test/api/v1/scopes", digdirRequests.single().url.toString())
    }

    @Test
    fun `an app with access to no scopes gets an empty list`() =
        scopesTest(filtering = { json(200, """{"result":{"exact":[],"prefix":[]}}""") }) {
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
    fun `gives 502 without calling Digdir when the filtering service fails`() = scopesTest(filtering = { json(500, "{}") }) {
        val response = listScopes()

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ErrorResponse("The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR), response.errorResponse())
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `a blank prefix from the filtering service gives 502 instead of every scope`() =
        scopesTest(filtering = { json(200, """{"result":{"exact":[],"prefix":[""]}}""") }) {
            val response = listScopes()

            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals(ErrorResponse("The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR), response.errorResponse())
            assertTrue(digdirRequests.isEmpty())
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
        assertEquals(
            listOf(
                "kartverk:tilgangsstyring/demo.read",
                "kartverk:tilgangsstyring/demo.write",
                "kartverk:dokumentbestilling",
                "kartverk:nrl.rapportering",
            ),
            response.names(),
        )
    }

    @Test
    fun `answers with Digdir's fields, but leaves out the ones this API does not know`() {
        val scope = digdirScope("kartverk:nrl.rapportering")
        val withNewFields = scope.replaceFirst("{", """{"description#de":"Lesezugriff","new_field":"from Digdir",""")

        scopesTest(filtering = null, digdir = { json(200, "[$withNewFields]") }) {
            val response = client.get("/api/scopes")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(Json.parseToJsonElement("[$scope]"), Json.parseToJsonElement(response.bodyAsText()))
        }
    }
}
