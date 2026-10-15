package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.auth.AccessTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/**
 * When Digdir answers with an error, a call throws a [DigdirErrorResponseException] that holds Digdir's answer. A
 * [DigdirException] means Digdir could not be called, or gave an answer this client cannot read.
 */
public class MaskinportenManagementClient(
    baseUrl: URI,
    private val httpClient: OutgoingHttpClient,
    private val accessTokenProvider: AccessTokenProvider,
) {
    private val baseUrl = baseUrl.toString().trimEnd('/')

    public suspend fun listScopes(filter: ScopeFilter? = null): List<ScopeDetails> {
        val scopes = send(HttpMethod.GET, SCOPES_PATH).decode<List<ScopeDetails>>()
        return if (filter == null) scopes else scopes.filter { filter.allows(it.name) }
    }

    public suspend fun listScopeAccess(scope: Scope): List<ScopeAccess> =
        send(HttpMethod.GET, "$SCOPE_ACCESS_PATH?scope=${encode(scope.value)}").decode()

    public suspend fun listConsumerScopeAccess(consumerOrgno: OrganizationNumber, filter: ScopeFilter? = null): List<ScopeAccess> {
        val access = send(HttpMethod.GET, "$SCOPE_ACCESS_PATH?consumer_orgno=$consumerOrgno").decode<List<ScopeAccess>>()
        return if (filter == null) access else access.filter { filter.allows(it.scope) }
    }

    public suspend fun grantScopeAccess(consumerOrgno: OrganizationNumber, scope: Scope): ScopeAccess =
        send(HttpMethod.PUT, "$SCOPE_ACCESS_PATH/$consumerOrgno?scope=${encode(scope.value)}").decode()

    public suspend fun removeScopeAccess(consumerOrgno: OrganizationNumber, scope: Scope): ScopeAccess =
        send(HttpMethod.DELETE, "$SCOPE_ACCESS_PATH/$consumerOrgno?scope=${encode(scope.value)}").decode()

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
        if (response.statusCode !in 200..299) {
            throw DigdirErrorResponseException(response.statusCode, response.contentType, response.body)
        }
        return response
    }

    private inline fun <T> callingDigdir(call: () -> T): T = try {
        call()
    } catch (e: IOException) {
        throw DigdirException("Call to Digdir failed: $e", e)
    }

    private inline fun <reified T> OutgoingResponse.decode(): T = try {
        json.decodeFromString<T>(body.decodeToString())
    } catch (e: IllegalArgumentException) {
        throw DigdirException("Digdir answered $statusCode with something this client cannot read: ${e.message}", e)
    }

    internal companion object {
        const val SCOPES_PATH = "/api/v1/scopes"

        const val SCOPE_ACCESS_PATH = "/api/v1/scopes/access"

        val json = Json { ignoreUnknownKeys = true }
    }
}
