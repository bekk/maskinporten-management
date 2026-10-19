package no.kartverket.__pkg__.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.kartverket.__pkg__.client.exampleItemJson
import no.kartverket.__pkg__.client.http.OutgoingRequest
import no.kartverket.__pkg__.client.json
import no.kartverket.__pkg__.client.recording
import no.kartverket.__pkg__.client.validation.ValidationCode
import no.kartverket.__pkg__.restserver.models.ErrorCode
import no.kartverket.__pkg__.restserver.models.FieldError
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    private val sent = mutableListOf<OutgoingRequest>()

    @Test
    fun `the liveness check answers 200`() = testServer(recording(sent) { error("not called") }) {
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
    }

    @Test
    fun `gets an example item from __Upstream__ with our token`() = testServer(recording(sent) { json(200, exampleItemJson()) }) {
        val response = client.get("/api/example-items/abc")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"id":"abc","name":"Example"}""", response.bodyAsText())
        assertEquals(URI("https://upstream.test/example-items/abc"), sent.single().url)
        assertEquals("Bearer test-token", sent.single().headers["Authorization"])
    }

    @Test
    fun `an invalid id gives 400 with the field, without calling __Upstream__`() = testServer(recording(sent) { error("not called") }) {
        val response = client.get("/api/example-items/not%20valid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.errorResponse()
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code)
        assertEquals(listOf("id"), error.errors?.map(FieldError::field))
        assertEquals(ValidationCode.INVALID_FORMAT, error.errors?.single()?.code)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a 4xx from __Upstream__ about the request keeps its status, but not its body`() =
        testServer(recording(sent) { json(404, """{"secret":"upstream detail"}""") }) {
            val response = client.get("/api/example-items/abc")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                """{"error":"__Upstream__ rejected the request","code":"UPSTREAM_REJECTED"}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `a 4xx from __Upstream__ about our token or client gives 502`() {
        for (status in listOf(401, 403, 429)) {
            testServer(recording(sent) { json(status, "") }) {
                val response = client.get("/api/example-items/abc")

                assertEquals(HttpStatusCode.BadGateway, response.status, "for $status")
                assertEquals(ErrorCode.UPSTREAM_ERROR, response.errorResponse().code, "for $status")
            }
        }
    }

    @Test
    fun `a 5xx from __Upstream__ gives 502`() = testServer(recording(sent) { json(503, "") }) {
        val response = client.get("/api/example-items/abc")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ErrorCode.UPSTREAM_ERROR, response.errorResponse().code)
    }

    @Test
    fun `no answer from __Upstream__ gives 502`() = testServer({ throw IOException("Connection refused") }) {
        val response = client.get("/api/example-items/abc")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ErrorCode.UPSTREAM_ERROR, response.errorResponse().code)
    }

    @Test
    fun `an answer from __Upstream__ that cannot be read gives 502`() = testServer(recording(sent) { json(200, "not json") }) {
        val response = client.get("/api/example-items/abc")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ErrorCode.UPSTREAM_ERROR, response.errorResponse().code)
    }

    @Test
    fun `no token gives 502, without calling __Upstream__`() = testServer(
        recording(sent) { error("not called") },
        tokenProvider = { throw IOException("Connection refused") },
    ) {
        val response = client.get("/api/example-items/abc")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ErrorCode.UPSTREAM_ERROR, response.errorResponse().code)
        assertTrue(sent.isEmpty())
    }
}
