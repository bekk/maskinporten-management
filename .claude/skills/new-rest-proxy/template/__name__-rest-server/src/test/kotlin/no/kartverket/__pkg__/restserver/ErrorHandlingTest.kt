package no.kartverket.__pkg__.restserver

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorHandlingTest {

    @Serializable
    private data class Body(val name: String)

    private fun answering(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                get("/fails") { throw IllegalStateException("secret detail") }
                get("/refused") { throw BadRequestException("Can't transform call to resource") }
                post("/body") { call.respond(call.receive<Body>()) }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.postBody(body: String): HttpResponse = client.post("/body") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    // Ktor's client cannot send a broken %-escape, so the check is tested on its own
    @Test
    fun `a URL with a broken escape is told apart from one that decodes`() {
        assertTrue(isBrokenUrl("/api/example-items?id=%zz"))
        assertTrue(isBrokenUrl("/api/example%zzitems/abc"))
        assertFalse(isBrokenUrl("/api/example-items/a%20b"))
    }

    @Test
    fun `a body that is not JSON gives 400 with MALFORMED_REQUEST`() = answering {
        val response = postBody("not json")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"The request could not be read","code":"MALFORMED_REQUEST"}""", response.bodyAsText())
    }

    @Test
    fun `a body with a field of the wrong type gives 400 with MALFORMED_REQUEST`() = answering {
        val response = postBody("""{"name":{}}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"The request could not be read","code":"MALFORMED_REQUEST"}""", response.bodyAsText())
    }

    @Test
    fun `a body with fields this API does not know is accepted`() = answering {
        val response = postBody("""{"name":"x","unknown":1}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"name":"x"}""", response.bodyAsText())
    }

    @Test
    fun `a request Ktor refuses gives 400 with MALFORMED_REQUEST, without blaming the URL`() = answering {
        val response = client.get("/refused")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"The request could not be read","code":"MALFORMED_REQUEST"}""", response.bodyAsText())
    }

    @Test
    fun `an unexpected error gives 500 with its code, but not its message`() = answering {
        val response = client.get("/fails")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("""{"error":"Internal server error","code":"INTERNAL_ERROR"}""", response.bodyAsText())
    }
}
