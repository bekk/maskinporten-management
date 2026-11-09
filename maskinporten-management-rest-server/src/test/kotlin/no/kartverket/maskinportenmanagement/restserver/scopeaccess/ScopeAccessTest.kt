package no.kartverket.maskinportenmanagement.restserver.scopeaccess

import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScopeAccess
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.restserver.errorResponse
import no.kartverket.maskinportenmanagement.restserver.problem.FieldError
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.testServer
import no.kartverket.maskinportenmanagement.restserver.validationFailed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeAccessTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun scopeAccessTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(
            recording(sent) { if (it.method.name == "GET") json(200, "[]") else json(200, digdirScopeAccess("kartverk:matrikkel.read")) },
            filtering = null,
            block,
        )

    private val changeMethods = listOf(HttpMethod.Put, HttpMethod.Delete)

    private suspend fun ApplicationTestBuilder.call(method: HttpMethod, pathAndQuery: String): HttpResponse =
        client.request(pathAndQuery) { this.method = method }

    @Test
    fun `sends the scope to Digdir`() {
        for (query in listOf("?scope=kartverk:matrikkel.read", "?scope=kartverk%3Amatrikkel.read")) {
            sent.clear()
            scopeAccessTest {
                assertEquals(HttpStatusCode.OK, call(HttpMethod.Get, "/api/scopeaccess/orgs$query").status, "for $query")
            }

            val request = sent.single()
            assertEquals("GET", request.method.name)
            assertEquals("/api/v1/scopes/access", request.url.path)
            assertEquals("scope=kartverk%3Amatrikkel.read", request.url.rawQuery, "for $query")
        }
    }

    @Test
    fun `giving and removing access send the method, the organisation and the scope to Digdir`() {
        for (method in changeMethods) {
            sent.clear()
            scopeAccessTest {
                assertEquals(HttpStatusCode.OK, call(method, "/api/scopeaccess/311718371?scope=kartverk:matrikkel.read").status)
            }

            val request = sent.single()
            assertEquals(method.value, request.method.name)
            assertEquals("/api/v1/scopes/access/311718371", request.url.path, "for $method")
            assertEquals("scope=kartverk%3Amatrikkel.read", request.url.rawQuery, "for $method")
        }
    }

    @Test
    fun `listing access without a scope is refused without calling Digdir`() = scopeAccessTest {
        val response = call(HttpMethod.Get, "/api/scopeaccess/orgs")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(validationFailed(FieldError("scope", ValidationCode.MISSING, "scope is required")), response.errorResponse())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a path that does not decode to one organisation number is refused without calling Digdir`() = scopeAccessTest {
        for (method in changeMethods) {
            for (consumerOrgno in listOf("%2E%2E", "%2E", "311718371%2F..", "orgs")) {
                val response = call(method, "/api/scopeaccess/$consumerOrgno?scope=kartverk:matrikkel.read")

                assertEquals(HttpStatusCode.BadRequest, response.status, "for $method $consumerOrgno")
                assertEquals(
                    validationFailed(invalidOrganisationNumber),
                    response.errorResponse(),
                    "for $method $consumerOrgno",
                )
            }
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `an invalid organisation number and an invalid scope are both listed, without calling Digdir`() = scopeAccessTest {
        for (method in changeMethods) {
            val response = call(method, "/api/scopeaccess/31171837?scope=kartverk:a+kartverk:b")

            assertEquals(HttpStatusCode.BadRequest, response.status, "for $method")
            assertEquals(
                validationFailed(
                    invalidOrganisationNumber,
                    FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace"),
                ),
                response.errorResponse(),
                "for $method",
            )
        }
        assertTrue(sent.isEmpty())
    }

    private val invalidOrganisationNumber =
        FieldError("consumerOrgno", ValidationCode.INVALID_FORMAT, "consumerOrgno must be an organisation number of 9 digits")
}
