package no.kartverket.maskinportenmanagement.client.filtering

import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import java.io.IOException
import java.net.URI

public class ExternalFilteringClient(url: String, private val httpClient: OutgoingHttpClient) {
    private val url = URI.create(url)

    public suspend fun filterFor(clientCertificate: String): ScopeFilter {
        val request = OutgoingRequest(
            method = "POST",
            url = url,
            headers = mapOf("Accept" to "application/json", CLIENT_CERTIFICATE_HEADER to clientCertificate),
        )
        val response = try {
            httpClient.send(request)
        } catch (e: IOException) {
            throw ExternalFilteringException("Call to the filtering service failed: $e", e)
        }
        if (response.statusCode !in 200..299) {
            throw ExternalFilteringException("The filtering service answered ${response.statusCode}")
        }
        return try {
            json.decodeFromString(ScopeFilter.serializer(), response.body.decodeToString())
        } catch (e: IllegalArgumentException) {
            throw ExternalFilteringException("The filtering service did not answer with exact and prefix: ${e.message}", e)
        }
    }

    public companion object {
        public const val CLIENT_CERTIFICATE_HEADER: String = "X-Forwarded-Client-Cert"
    }
}

public class ExternalFilteringException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private val json = Json { ignoreUnknownKeys = true }
