package no.kartverket.maskinportenmanagement.client

import no.kartverket.maskinportenmanagement.client.auth.AccessTokenProvider
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

public class MaskinportenManagementClient(
    baseUrl: URI,
    private val httpClient: OutgoingHttpClient,
    private val accessTokenProvider: AccessTokenProvider,
) {
    private val baseUrl = baseUrl.toString().trimEnd('/')

    public suspend fun listScopeAccess(scope: Scope): OutgoingResponse =
        send(HttpMethod.GET, "$SCOPE_ACCESS_PATH?scope=${encode(scope.value)}")

    public suspend fun listConsumerScopeAccess(consumerOrgno: OrganizationNumber): OutgoingResponse =
        send(HttpMethod.GET, "$SCOPE_ACCESS_PATH?consumer_orgno=$consumerOrgno")

    public suspend fun grantScopeAccess(consumerOrgno: OrganizationNumber, scope: Scope): OutgoingResponse =
        send(HttpMethod.PUT, "$SCOPE_ACCESS_PATH/$consumerOrgno?scope=${encode(scope.value)}")

    public suspend fun removeScopeAccess(consumerOrgno: OrganizationNumber, scope: Scope): OutgoingResponse =
        send(HttpMethod.DELETE, "$SCOPE_ACCESS_PATH/$consumerOrgno?scope=${encode(scope.value)}")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private suspend fun send(method: HttpMethod, pathAndQuery: String): OutgoingResponse {
        val token = callingDigdir { accessTokenProvider.accessToken() }
        val request = OutgoingRequest(
            method = method,
            url = URI.create(baseUrl + pathAndQuery),
            headers = mapOf("Accept" to "application/json", "Authorization" to "Bearer ${token.value}"),
        )
        val response = callingDigdir { httpClient.send(request) }
        // Our token is wrong, not the caller's request, so the caller must not get the 401
        if (response.statusCode == 401) {
            accessTokenProvider.refused(token)
            throw DigdirException("Digdir refused our Maskinporten token with HTTP 401: ${response.body.decodeToString()}")
        }
        return response
    }

    private inline fun <T> callingDigdir(call: () -> T): T = try {
        call()
    } catch (e: IOException) {
        throw DigdirException("Call to Digdir failed: $e", e)
    }

    internal companion object {
        const val SCOPE_ACCESS_PATH = "/api/v1/scopes/access"
    }
}
