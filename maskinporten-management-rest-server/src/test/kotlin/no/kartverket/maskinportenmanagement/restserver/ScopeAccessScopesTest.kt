package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.kartverket.maskinportenmanagement.client.digdirScopeAccess
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.client.jsonList
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeAccessScopesTest {

    private val digdirRequests = mutableListOf<OutgoingRequest>()
    private val filteringRequests = mutableListOf<OutgoingRequest>()

    private val scopeAccess = jsonList(
        digdirScopeAccess("kartverk:tilgangsstyring/demo.read"),
        digdirScopeAccess("kartverk:tilgangsstyring/demo.write"),
        digdirScopeAccess("kartverk:dokumentbestilling"),
        digdirScopeAccess("kartverk:nrl.rapportering"),
    )

    private fun scopeAccessTest(
        filtering: ((OutgoingRequest) -> OutgoingResponse)? = {
            json(200, """{"result":{"exact":["kartverk:nrl.rapportering"],"prefix":["kartverk:tilgangsstyring/"]}}""")
        },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testServer(recording(digdirRequests) { json(200, scopeAccess) }, filtering?.let { recording(filteringRequests, it) }, block)

    private suspend fun ApplicationTestBuilder.listScopes(query: String = "?orgnr=311718371"): HttpResponse =
        client.get("/api/scopeaccess/scopes$query") { header("X-Forwarded-Client-Cert", CLIENT_CERTIFICATE) }

    private suspend fun HttpResponse.scopes(): List<String> =
        Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject.getValue("scope").jsonPrimitive.content }

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
    fun `sends the calling app's certificate as OPA input to the filtering service, and asks Digdir for the organisation's access`() =
        scopeAccessTest {
            listScopes()

            assertEquals(CLIENT_CERTIFICATE, filteringRequests.single().certificateSentToFiltering())
            assertEquals("https://digdir.test/api/v1/scopes/access?consumer_orgno=311718371", digdirRequests.single().url.toString())
        }

    @Test
    fun `a query that is not exactly one orgnr of 9 digits is refused before anything else is called`() = scopeAccessTest {
        val refusals = mapOf(
            "?orgnr=31171837" to validationFailed(FieldError("orgnr", ValidationCode.INVALID_FORMAT, "orgnr must be an organisation number of 9 digits")),
            "?orgnr=311718371&scope=kartverk:x" to malformedRequest("The query must contain only the parameter orgnr, sent once"),
        )
        for ((query, error) in refusals) {
            val response = listScopes(query)

            assertEquals(HttpStatusCode.BadRequest, response.status, "for \"$query\"")
            assertEquals(error, response.errorResponse(), "for \"$query\"")
        }
        assertTrue(filteringRequests.isEmpty())
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `a call without the certificate header is refused before anything else is called`() = scopeAccessTest {
        val response = client.get("/api/scopeaccess/scopes?orgnr=311718371")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(validationFailed(FieldError("X-Forwarded-Client-Cert", ValidationCode.MISSING, "X-Forwarded-Client-Cert is required")), response.errorResponse())
        assertTrue(filteringRequests.isEmpty())
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `gives 502 without calling Digdir when the filtering service fails`() = scopeAccessTest(filtering = { json(500, "{}") }) {
        val response = listScopes()

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(upstreamError("The call to the filtering service failed"), response.errorResponse())
        assertTrue(digdirRequests.isEmpty())
    }

    @Test
    fun `with filtering turned off, returns all of Digdir's answer and needs no header`() = scopeAccessTest(filtering = null) {
        val response = client.get("/api/scopeaccess/scopes?orgnr=311718371")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(Json.parseToJsonElement(scopeAccess), Json.parseToJsonElement(response.bodyAsText()))
    }
}
