package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpRequest
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.ConsumerAccess
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ScopeAccessTest {

    @Test
    fun `returns the consumers of the scope from Maskinporten`() = scopeAccessTest {
        val response = postScopeAccess()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            ScopeAccessResponse(
                listOf(
                    ConsumerAccess(
                        scope = SAMPLE_SCOPE,
                        ownerOrgno = "971040238",
                        ownerOrganizationName = "Statens kartverk",
                        consumerOrgno = "971032081",
                        consumerOrganizationName = "Statens vegvesen",
                        state = ScopeAccessState.APPROVED,
                        created = "2026-03-01T08:00:00Z",
                        lastUpdated = "2026-03-02T08:00:00Z",
                    ),
                ),
            ),
            response.scopeAccessResponse(),
        )
    }

    @Test
    fun `asks Maskinporten for the scope from the body`() {
        val requests = mutableListOf<ManagementHttpRequest>()
        scopeAccessTest(requests = requests) {
            postScopeAccess()
        }

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("scope=kartverk%3Amatrikkel.read", request.url.rawQuery)
    }

    @Test
    fun `a scope without consumers gives an empty list`() = scopeAccessTest(body = "[]") {
        val response = postScopeAccess()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ScopeAccessResponse(emptyList()), response.scopeAccessResponse())
    }

    @Test
    fun `rejects a missing or malformed scope without calling Maskinporten`() {
        val cases = mapOf(
            "{}" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            """{"scope":null}""" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            """{"scope":"  "}""" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            """{"scope":"kartverk"}""" to FieldError(
                "scope",
                ValidationCode.INVALID_FORMAT,
                "scope must be a prefix and a subscope separated by a colon, e.g. \"kartverk:matrikkel.read\"",
            ),
        )

        for ((body, expected) in cases) {
            val requests = mutableListOf<ManagementHttpRequest>()
            scopeAccessTest(requests = requests) {
                val response = postScopeAccess(body)

                assertEquals(HttpStatusCode.BadRequest, response.status, body)
                assertEquals(
                    ErrorResponse("Validation failed", ErrorCode.VALIDATION_ERROR, listOf(expected)),
                    response.errorResponse(),
                    body,
                )
            }
            assertEquals(emptyList(), requests, body)
        }
    }

    @Test
    fun `a body that is not json, or has the wrong types, is MALFORMED_BODY`() {
        for (body in listOf("not json", """{"scope":42}""", """{"scope":["$SAMPLE_SCOPE"]}""")) {
            scopeAccessTest {
                val response = postScopeAccess(body)

                assertEquals(HttpStatusCode.BadRequest, response.status, body)
                assertEquals(ErrorResponse("Malformed request body", ErrorCode.MALFORMED_BODY), response.errorResponse(), body)
            }
        }
    }

    @Test
    fun `without a Content-Type header returns 400, not 500`() = scopeAccessTest {
        assertEquals(HttpStatusCode.BadRequest, postScopeAccess(json = false).status)
    }

    @Test
    fun `the upstream status decides whether the caller or Maskinporten is to blame`() {
        val cases = listOf(
            Triple(400, HttpStatusCode.BadRequest, ErrorCode.UPSTREAM_REJECTED),
            Triple(404, HttpStatusCode.BadGateway, ErrorCode.UPSTREAM_ERROR),
            Triple(500, HttpStatusCode.BadGateway, ErrorCode.UPSTREAM_ERROR),
        )

        for ((upstream, expected, expectedCode) in cases) {
            scopeAccessTest(statusCode = upstream, body = """{"error":"secret upstream detail"}""") {
                val response = postScopeAccess()
                val text = response.bodyAsText()

                assertEquals(expected, response.status, "upstream $upstream")
                assertEquals(expectedCode, Json.decodeFromString(ErrorResponse.serializer(), text).code, "upstream $upstream")
                assertFalse(text.contains("secret upstream detail"), "leaks Maskinporten's body: $text")
            }
        }
    }

    @Test
    fun `an unparseable answer or a failed connection is Maskinporten's fault`() {
        scopeAccessTest(body = "not json") {
            assertEquals(HttpStatusCode.BadGateway, postScopeAccess().status)
        }
        scopeAccessTest(failure = IOException("connection refused")) {
            assertEquals(HttpStatusCode.BadGateway, postScopeAccess().status)
        }
    }

    @Test
    fun `an unexpected exception is our fault, not the caller's`() =
        scopeAccessTest(failure = IllegalStateException("internal detail")) {
            val response = postScopeAccess()

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(ErrorResponse("Internal server error", ErrorCode.INTERNAL_ERROR), response.errorResponse())
        }
}
