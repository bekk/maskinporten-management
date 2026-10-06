package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import no.kartverket.maskinportenmanagement.client.exception.MaskinportenApiException
import no.kartverket.maskinportenmanagement.client.http.Http
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpClient
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpRequest
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpResponse
import no.kartverket.maskinportenmanagement.client.model.AccessResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

public class MaskinportenManagementClient(
    apiBaseUrl: String,
    private val httpClient: ManagementHttpClient,
) {
    private val apiBaseUrl: String = apiBaseUrl.trimEnd('/')

    /** Lists every consumer organization that has, has requested or has been denied access to [scope]. */
    public suspend fun getScopeAccess(scope: ScopeName): List<ScopeAccess> {
        val request = ManagementHttpRequest(
            method = "GET",
            url = Http.url(apiBaseUrl, "$SCOPE_ACCESS_PATH?scope=${urlEncode(scope.value)}"),
            headers = mapOf("Accept" to "application/json"),
        )

        val response = Http.sendExpectingOk(httpClient, request, "Maskinporten API", ::MaskinportenApiException)
        return scopeAccessOf(response)
    }

    private fun scopeAccessOf(response: ManagementHttpResponse): List<ScopeAccess> {
        val parsed = try {
            Http.json.decodeFromString(ListSerializer(AccessResponse.serializer()), response.body)
        } catch (e: SerializationException) {
            throw MaskinportenApiException(
                "Failed to parse the scope access response: ${e.message}",
                response.statusCode,
                response.body,
                e,
            )
        }
        return parsed.map(AccessResponse::toScopeAccess)
    }

    internal companion object {
        const val SCOPE_ACCESS_PATH = "/scopes/access"

        private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
