package no.kartverket.maskinportenmanagement.client.filtering

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import java.io.IOException
import java.net.URI

public class ExternalFilteringClient(private val url: URI, private val httpClient: OutgoingHttpClient) {
    public suspend fun filterFor(clientCertificate: ClientCertificate): ScopeFilter {
        val requestBody = FilteringRequest(FilteringInput(mapOf(CLIENT_CERTIFICATE_HEADER.lowercase() to clientCertificate.value)))
        val request = OutgoingRequest(
            method = HttpMethod.POST,
            url = url,
            headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json"),
            body = json.encodeToString(FilteringRequest.serializer(), requestBody).encodeToByteArray(),
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
            val envelope = json.decodeFromString(FilteringResponse.serializer(), response.body.decodeToString())
            envelope.result ?: ScopeFilter(emptyList(), emptyList())
        } catch (e: IllegalArgumentException) {
            throw ExternalFilteringException("The filtering service did not answer with a valid filter: ${e.message}", e)
        }
    }

    public companion object {
        public const val CLIENT_CERTIFICATE_HEADER: String = "X-Forwarded-Client-Cert"
    }
}

public class ExternalFilteringException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class FilteringRequest(val input: FilteringInput)

@Serializable
private data class FilteringInput(val headers: Map<String, String>)

@Serializable
private data class FilteringResponse(val result: ScopeFilter? = null)
