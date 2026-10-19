package no.kartverket.__pkg__.client

import kotlinx.coroutines.runBlocking
import no.kartverket.__pkg__.client.auth.AccessToken
import no.kartverket.__pkg__.client.auth.AccessTokenProvider
import no.kartverket.__pkg__.client.exception.TokenException
import no.kartverket.__pkg__.client.exception.__Upstream__Exception
import no.kartverket.__pkg__.client.http.HttpMethod
import no.kartverket.__pkg__.client.http.OutgoingHttpClient
import no.kartverket.__pkg__.client.http.OutgoingRequest
import no.kartverket.__pkg__.client.validation.ValidationCode
import no.kartverket.__pkg__.client.validation.ValidationException
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class __Name__ClientTest {

    private val requests = mutableListOf<OutgoingRequest>()

    private fun client(
        tokenProvider: AccessTokenProvider = AccessTokenProvider { AccessToken.of("test-token") },
        httpClient: OutgoingHttpClient = recording(requests) { json(200, exampleItemJson()) },
    ) = __Name__Client(URI("https://upstream.test/"), httpClient, tokenProvider)

    @Test
    fun `gets an example item with our token`() = runBlocking {
        val item = client().getExampleItem("abc")

        assertEquals(ExampleItem("abc", "Example"), item)
        val request = requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals(URI("https://upstream.test/example-items/abc"), request.url)
        assertEquals("Bearer test-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `refuses an id that cannot be right, without calling __Upstream__`() {
        for ((id, code) in listOf(" " to ValidationCode.MISSING, "a/b" to ValidationCode.INVALID_FORMAT)) {
            val e = assertFailsWith<ValidationException>(id) { runBlocking { client().getExampleItem(id) } }

            assertEquals("id", e.errors.single().field, id)
            assertEquals(code, e.errors.single().code, id)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an error answer keeps its status and body`() {
        val e = assertFailsWith<__Upstream__Exception> {
            runBlocking { client(httpClient = recording(requests) { json(404, """{"error":"not found"}""") }).getExampleItem("abc") }
        }

        assertEquals(404, e.statusCode)
        assertEquals("""{"error":"not found"}""", e.responseBody)
    }

    @Test
    fun `a refused token is handed back, so the next call gets a new one`() {
        val refused = mutableListOf<AccessToken>()
        val tokenProvider = object : AccessTokenProvider {
            override suspend fun accessToken() = AccessToken.of("old-token")

            override suspend fun refused(token: AccessToken) {
                refused += token
            }
        }

        val e = assertFailsWith<__Upstream__Exception> {
            runBlocking { client(tokenProvider, recording(requests) { json(401, "") }).getExampleItem("abc") }
        }

        assertEquals(401, e.statusCode)
        assertEquals(listOf(AccessToken.of("old-token")), refused)
    }

    @Test
    fun `no answer gives an exception without a status`() {
        val e = assertFailsWith<__Upstream__Exception> {
            runBlocking { client(httpClient = { throw IOException("Connection refused") }).getExampleItem("abc") }
        }

        assertNull(e.statusCode)
        assertContains(e.message!!, "Connection refused")
    }

    @Test
    fun `an answer this client cannot read gives an exception`() {
        val e = assertFailsWith<__Upstream__Exception> {
            runBlocking { client(httpClient = recording(requests) { json(200, "not json") }).getExampleItem("abc") }
        }

        assertEquals(200, e.statusCode)
    }

    @Test
    fun `a failure to get a token is told apart from a failure to call __Upstream__`() {
        assertFailsWith<TokenException> {
            runBlocking { client(tokenProvider = { throw IOException("Connection refused") }).getExampleItem("abc") }
        }
        assertTrue(requests.isEmpty())
    }
}
