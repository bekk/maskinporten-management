package no.kartverket.maskinportenmanagement.client.filtering

import kotlinx.coroutines.runBlocking
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpRequest
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalFilteringClientTest {

    private val sent = mutableListOf<DigdirHttpRequest>()
    private val certificate = "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

    private fun filteringAnswering(answer: (DigdirHttpRequest) -> DigdirHttpResponse) =
        ExternalFilteringClient(
            "https://filtering.test/scopes",
            DigdirHttpClient { request ->
                sent += request
                answer(request)
            },
        )

    private fun answer(status: Int, body: String) = DigdirHttpResponse(status, "application/json", body.toByteArray())

    @Test
    fun `posts the client certificate to the URL and reads exact and prefix`() = runBlocking {
        val filtering = filteringAnswering {
            answer(200, """{"exact":["kartverk:nrl.rapportering"],"prefix":["kartverk:tilgangsstyring/"]}""")
        }

        val filter = filtering.filterFor(certificate)

        assertEquals(ScopeFilter(listOf("kartverk:nrl.rapportering"), listOf("kartverk:tilgangsstyring/")), filter)
        val request = sent.single()
        assertEquals("POST", request.method)
        assertEquals("https://filtering.test/scopes", request.url.toString())
        assertEquals(certificate, request.headers["X-Forwarded-Client-Cert"])
    }

    @Test
    fun `ignores fields it does not know`() = runBlocking {
        val filtering = filteringAnswering { answer(200, """{"exact":[],"prefix":[],"actions":["scopes/read"]}""") }

        assertEquals(ScopeFilter(emptyList(), emptyList()), filtering.filterFor(certificate))
    }

    @Test
    fun `fails when the answer is not exact and prefix`() {
        for (body in listOf("""{"exact":[]}""", """{"exact":[null],"prefix":[]}""", "[]", "not json", "")) {
            val filtering = filteringAnswering { answer(200, body) }

            assertFailsWith<ExternalFilteringException>(body) { runBlocking { filtering.filterFor(certificate) } }
        }
    }

    @Test
    fun `fails when the filtering service answers with an error`() {
        for (status in listOf(400, 403, 404, 500, 503)) {
            val filtering = filteringAnswering { answer(status, """{"exact":[],"prefix":[]}""") }

            val e = assertFailsWith<ExternalFilteringException> { runBlocking { filtering.filterFor(certificate) } }

            assertContains(e.message!!, "$status")
        }
    }

    @Test
    fun `fails when the filtering service cannot be reached`() {
        val filtering = filteringAnswering { throw IOException("Connection refused") }

        val e = assertFailsWith<ExternalFilteringException> { runBlocking { filtering.filterFor(certificate) } }

        assertContains(e.message!!, "Connection refused")
    }
}
