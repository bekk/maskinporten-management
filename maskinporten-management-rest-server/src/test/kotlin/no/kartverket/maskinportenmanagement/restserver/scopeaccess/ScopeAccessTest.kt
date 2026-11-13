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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
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

    private suspend fun ApplicationTestBuilder.call(method: HttpMethod, pathAndQuery: String): HttpResponse =
        client.request(pathAndQuery) { this.method = method }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["?scope=kartverk:matrikkel.read", "?scope=kartverk%3Amatrikkel.read"])
    fun `sends the scope to Digdir`(query: String) {
        scopeAccessTest {
            assertEquals(HttpStatusCode.OK, call(HttpMethod.Get, "/api/scopeaccess/orgs$query").status)
        }

        val request = sent.single()
        assertEquals("GET", request.method.name)
        assertEquals("/api/v1/scopes/access", request.url.path)
        assertEquals("scope=kartverk%3Amatrikkel.read", request.url.rawQuery)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["PUT", "DELETE"])
    fun `giving and removing access send the method, the organisation and the scope to Digdir`(method: String) {
        scopeAccessTest {
            assertEquals(HttpStatusCode.OK, call(HttpMethod.parse(method), "/api/scopeaccess/311718371?scope=kartverk:matrikkel.read").status)
        }

        val request = sent.single()
        assertEquals(method, request.method.name)
        assertEquals("/api/v1/scopes/access/311718371", request.url.path)
        assertEquals("scope=kartverk%3Amatrikkel.read", request.url.rawQuery)
    }

    @Test
    fun `listing access without a scope is refused without calling Digdir`() = scopeAccessTest {
        val response = call(HttpMethod.Get, "/api/scopeaccess/orgs")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(validationFailed(FieldError("scope", ValidationCode.MISSING, "scope is required")), response.errorResponse())
        assertTrue(sent.isEmpty())
    }

    @ParameterizedTest(name = "{0} /api/scopeaccess/{1}")
    @MethodSource("changeMethodsAndPathsThatAreNotAnOrganisationNumber")
    fun `a path that does not decode to one organisation number is refused without calling Digdir`(
        method: String,
        consumerOrgno: String,
    ) = scopeAccessTest {
        val response = call(HttpMethod.parse(method), "/api/scopeaccess/$consumerOrgno?scope=kartverk:matrikkel.read")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(validationFailed(invalidOrganisationNumber), response.errorResponse())
        assertTrue(sent.isEmpty())
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["PUT", "DELETE"])
    fun `an invalid organisation number and an invalid scope are both listed, without calling Digdir`(method: String) = scopeAccessTest {
        val response = call(HttpMethod.parse(method), "/api/scopeaccess/31171837?scope=kartverk:a+kartverk:b")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            validationFailed(
                invalidOrganisationNumber,
                FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace"),
            ),
            response.errorResponse(),
        )
        assertTrue(sent.isEmpty())
    }

    private val invalidOrganisationNumber =
        FieldError("consumerOrgno", ValidationCode.INVALID_FORMAT, "consumerOrgno must be an organisation number of 9 digits")

    companion object {
        @JvmStatic
        fun changeMethodsAndPathsThatAreNotAnOrganisationNumber(): List<Arguments> = listOf("PUT", "DELETE").flatMap { method ->
            listOf("%2E%2E", "%2E", "311718371%2F..", "orgs").map { Arguments.of(method, it) }
        }
    }
}
