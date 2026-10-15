package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorHandlingTest {

    private fun answerTo(failure: Throwable, check: suspend (HttpResponse) -> Unit) = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing { get("/fails") { throw failure } }
        }
        check(client.get("/fails"))
    }

    // Ktor's client cannot send a broken %-escape, so the check is tested on its own
    @Test
    fun `a URL with a broken escape is told apart from one that decodes`() {
        assertTrue(isBrokenUrl("/api/scopes?scope=%zz"))
        assertTrue(isBrokenUrl("/api/scope%zzaccess/orgs"))
        assertFalse(isBrokenUrl("/api/scopeaccess/orgs?scope=kartverk%3Amatrikkel.read"))
    }

    @Test
    fun `a request Ktor refuses for another reason gives 400 with INVALID_REQUEST, without blaming the URL`() =
        answerTo(BadRequestException("Can't transform call to resource")) { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("""{"error":"The request could not be read","code":"INVALID_REQUEST"}""", response.bodyAsText())
        }

    @Test
    fun `an unexpected error gives 500 with its code, but not its message`() =
        answerTo(IllegalStateException("secret detail")) { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("""{"error":"Internal server error","code":"INTERNAL_ERROR"}""", response.bodyAsText())
        }
}
