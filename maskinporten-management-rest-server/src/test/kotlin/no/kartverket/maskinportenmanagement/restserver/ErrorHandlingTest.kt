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

class ErrorHandlingTest {

    private fun answerTo(failure: Throwable, check: suspend (HttpResponse) -> Unit) = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing { get("/fails") { throw failure } }
        }
        check(client.get("/fails"))
    }

    // Netty throws this when the path or query has a broken %-escape, such as %zz
    @Test
    fun `a URL Ktor cannot decode gives 400 with INVALID_REQUEST`() =
        answerTo(BadRequestException("Unable to decode query parameters")) { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("""{"error":"The URL is not encoded correctly","code":"INVALID_REQUEST"}""", response.bodyAsText())
        }

    @Test
    fun `an unexpected error gives 500 with its code, but not its message`() =
        answerTo(IllegalStateException("secret detail")) { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("""{"error":"Internal server error","code":"INTERNAL_ERROR"}""", response.bodyAsText())
        }
}
