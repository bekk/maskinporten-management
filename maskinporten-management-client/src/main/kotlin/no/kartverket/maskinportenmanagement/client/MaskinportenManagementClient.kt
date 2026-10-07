package no.kartverket.maskinportenmanagement.client

import no.kartverket.maskinportenmanagement.client.http.DigdirHttpClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpRequest
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

public class MaskinportenManagementClient(
    baseUrl: String,
    private val httpClient: DigdirHttpClient,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    public suspend fun listScopeAccess(scope: String): DigdirHttpResponse =
        get("$SCOPE_ACCESS_PATH?scope=${URLEncoder.encode(scope, Charsets.UTF_8)}")

    private suspend fun get(pathAndQuery: String): DigdirHttpResponse {
        val request = DigdirHttpRequest(
            method = "GET",
            url = URI.create(baseUrl + pathAndQuery),
            headers = mapOf("Accept" to "application/json"),
        )
        return try {
            httpClient.send(request)
        } catch (e: IOException) {
            throw DigdirException("Call to Digdir failed: $e", e)
        }
    }

    internal companion object {
        const val SCOPE_ACCESS_PATH = "/api/v1/scopes/access"
    }
}
