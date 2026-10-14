package no.kartverket.maskinportenmanagement.client.filtering

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalFilteringClientTest {

    private val sent = mutableListOf<OutgoingRequest>()
    private val certificate =
        ClientCertificate.of("By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app")

    private fun filteringAnswering(answer: (OutgoingRequest) -> OutgoingResponse) =
        ExternalFilteringClient(
            URI("https://filtering.test/scopes"),
            OutgoingHttpClient { request ->
                sent += request
                answer(request)
            },
        )

    private fun answer(status: Int, body: String) = OutgoingResponse(status, "application/json", body.toByteArray())

    @Serializable
    private data class SentRequest(val input: SentInput)

    @Serializable
    private data class SentInput(val headers: Map<String, String>)

    @Test
    fun `posts the client certificate as input headers and reads exact and prefix from the result`() = runBlocking {
        val filtering = filteringAnswering {
            answer(200, """{"result":{"exact":["kartverk:nrl.rapportering"],"prefix":["kartverk:tilgangsstyring/"]}}""")
        }

        val filter = filtering.filterFor(certificate)

        assertEquals(ScopeFilter(listOf("kartverk:nrl.rapportering"), listOf("kartverk:tilgangsstyring/")), filter)
        val request = sent.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://filtering.test/scopes", request.url.toString())
        assertEquals("application/json", request.headers["Content-Type"])
        val sentRequest = Json.decodeFromString(SentRequest.serializer(), request.body!!.decodeToString())
        assertEquals(certificate.value, sentRequest.input.headers["x-forwarded-client-cert"])
    }

    @Test
    fun `ignores fields it does not know`() = runBlocking {
        val filtering =
            filteringAnswering { answer(200, """{"result":{"exact":[],"prefix":[],"actions":["scopes/read"]}}""") }

        assertEquals(ScopeFilter(emptyList(), emptyList()), filtering.filterFor(certificate))

        val envelopeWithExtras =
            filteringAnswering { answer(200, """{"result":{"exact":[],"prefix":[]},"decision_id":"abc"}""") }

        assertEquals(ScopeFilter(emptyList(), emptyList()), envelopeWithExtras.filterFor(certificate))
    }

    @Test
    fun `treats a missing result as an undefined document, i e no access`() = runBlocking {
        val filtering = filteringAnswering { answer(200, "{}") }

        assertEquals(ScopeFilter(emptyList(), emptyList()), filtering.filterFor(certificate))
    }

    @Test
    fun `fails when the answer is not exact and prefix`() {
        for (body in listOf(
            """{"result":{"exact":[]}}""",
            """{"result":{"exact":[null],"prefix":[]}}""",
            """{"result":[]}""",
            "[]",
            "not json",
            "",
        )) {
            val filtering = filteringAnswering { answer(200, body) }

            assertFailsWith<ExternalFilteringException>(body) { runBlocking { filtering.filterFor(certificate) } }
        }
    }

    @Test
    fun `fails when a prefix is blank, since it would allow every scope`() {
        val filtering =
            filteringAnswering { answer(200, """{"result":{"exact":[],"prefix":["kartverk:tilgangsstyring/",""]}}""") }

        val e = assertFailsWith<ExternalFilteringException> { runBlocking { filtering.filterFor(certificate) } }

        assertContains(e.message!!, "must not be blank")
    }

    @Test
    fun `fails when the filtering service answers with an error`() {
        for (status in listOf(400, 403, 404, 500, 503)) {
            val filtering = filteringAnswering { answer(status, """{"result":{"exact":[],"prefix":[]}}""") }

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
