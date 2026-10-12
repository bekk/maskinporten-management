package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource

// Logs only the method and URL, never headers or bodies: they hold our Maskinporten token
internal class LoggingOutgoingHttpClient(private val httpClient: OutgoingHttpClient) : OutgoingHttpClient {
    override suspend fun send(request: OutgoingRequest): OutgoingResponse {
        val call = "${request.method} ${request.url}"
        log.info("Calling $call")
        val start = TimeSource.Monotonic.markNow()
        val response = try {
            httpClient.send(request)
        } catch (e: Throwable) {
            log.warn("$call failed after ${start.elapsedNow().inWholeMilliseconds} ms: $e")
            throw e
        }
        log.info("$call answered ${response.statusCode} in ${start.elapsedNow().inWholeMilliseconds} ms")
        return response
    }

    private companion object {
        val log = LoggerFactory.getLogger("outgoing")
    }
}
