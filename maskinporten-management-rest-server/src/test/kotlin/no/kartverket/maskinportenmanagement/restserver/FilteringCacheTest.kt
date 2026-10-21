package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.client.jsonList
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class FilteringCacheTest {

    private val filteringRequests = mutableListOf<OutgoingRequest>()
    private var filteringAnswer = json(200, """{"result":{"exact":["kartverk:nrl.rapportering"],"prefix":[]}}""")

    private fun cachingTest(block: suspend ApplicationTestBuilder.() -> Unit) = testServer(
        digdir = { json(200, jsonList(digdirScope("kartverk:nrl.rapportering"))) },
        externalFiltering = ExternalFiltering(
            ExternalFilteringClient(URI("https://filtering.test/scopes"), recording(filteringRequests) { filteringAnswer }),
            cacheDuration = Duration.ofMinutes(1),
        ),
        block = block,
    )

    private suspend fun ApplicationTestBuilder.listScopes(certificate: String = CLIENT_CERTIFICATE): HttpStatusCode =
        client.get("/api/scopes") { header("X-Forwarded-Client-Cert", certificate) }.status

    @Test
    fun `asks the filtering service once per caller while the answer is remembered`() = cachingTest {
        assertEquals(HttpStatusCode.OK, listScopes())
        assertEquals(HttpStatusCode.OK, listScopes())
        assertEquals(1, filteringRequests.size)

        assertEquals(HttpStatusCode.OK, listScopes("URI=spiffe://cluster.local/ns/other/sa/app"))
        assertEquals(2, filteringRequests.size)
    }

    @Test
    fun `does not remember an answer that failed`() = cachingTest {
        filteringAnswer = OutgoingResponse(503, "text/plain", "unavailable".toByteArray())
        assertEquals(HttpStatusCode.BadGateway, listScopes())

        filteringAnswer = json(200, """{"result":{"exact":["kartverk:nrl.rapportering"],"prefix":[]}}""")
        assertEquals(HttpStatusCode.OK, listScopes())
        assertEquals(2, filteringRequests.size)
    }
}
