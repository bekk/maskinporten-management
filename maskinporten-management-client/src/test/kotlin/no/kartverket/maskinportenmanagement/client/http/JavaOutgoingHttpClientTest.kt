package no.kartverket.maskinportenmanagement.client.http

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaOutgoingHttpClientTest {

    private val noRedirects = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    @Test
    fun `refuses an HttpClient that follows redirects`() {
        val following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

        val e = assertFailsWith<IllegalArgumentException> { JavaOutgoingHttpClient(following, Duration.ofSeconds(3)) }

        assertContains(e.message!!, "redirects")
    }

    @Test
    fun `rejects a request timeout that is not positive`() {
        val e = assertFailsWith<IllegalArgumentException> { JavaOutgoingHttpClient(noRedirects, Duration.ZERO) }

        assertContains(e.message!!, "positive")
    }

    @Test
    fun `returns the status, content type and body bytes as the server sent them`() {
        val body = """{"error":"ikke funnet: æøå"}""".toByteArray(Charsets.ISO_8859_1)
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/json;charset=ISO-8859-1")
                exchange.sendResponseHeaders(404, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val url = URI("http://localhost:${server.address.port}/api/v1/scopes/access")
            val response = runBlocking {
                JavaOutgoingHttpClient(noRedirects, Duration.ofSeconds(3)).send(OutgoingRequest("GET", url, emptyMap()))
            }

            assertEquals(404, response.statusCode)
            assertEquals("application/json;charset=ISO-8859-1", response.contentType)
            assertContentEquals(body, response.body)
        } finally {
            server.stop(0)
        }
    }
}
