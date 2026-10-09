package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import no.kartverket.maskinportenmanagement.client.auth.AccessTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

public class MaskinportenManagementClient(
    baseUrl: String,
    private val httpClient: OutgoingHttpClient,
    private val accessTokenProvider: AccessTokenProvider,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    public suspend fun listScopes(filter: ScopeFilter? = null): OutgoingResponse {
        val response = send("GET", SCOPES_PATH)
        return if (filter == null) response else response.keepingOnly("name", filter)
    }

    public suspend fun listScopeAccess(scope: String): OutgoingResponse =
        send("GET", "$SCOPE_ACCESS_PATH?scope=${encode(scope)}")

    public suspend fun grantScopeAccess(consumerOrgno: String, scope: String): OutgoingResponse =
        send("PUT", "$SCOPE_ACCESS_PATH/${pathSegment(consumerOrgno)}?scope=${encode(scope)}")

    public suspend fun removeScopeAccess(consumerOrgno: String, scope: String): OutgoingResponse =
        send("DELETE", "$SCOPE_ACCESS_PATH/${pathSegment(consumerOrgno)}?scope=${encode(scope)}")

    // In the path, ".." would turn DELETE .../access/.. into DELETE /api/v1/scopes: deleting the scope itself
    private fun pathSegment(consumerOrgno: String): String {
        require(ORGANIZATION_NUMBER.matches(consumerOrgno)) {
            "consumerOrgno must be an organisation number of 9 digits, but was \"$consumerOrgno\""
        }
        return consumerOrgno
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private suspend fun send(method: String, pathAndQuery: String): OutgoingResponse {
        val token = callingDigdir { accessTokenProvider.accessToken() }
        val request = OutgoingRequest(
            method = method,
            url = URI.create(baseUrl + pathAndQuery),
            headers = mapOf("Accept" to "application/json", "Authorization" to "Bearer $token"),
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

    // Keeps the items whose field is allowed. Anything but a JSON list of objects in a 2xx answer is an error, so
    // nothing unfiltered gets through
    private fun OutgoingResponse.keepingOnly(field: String, filter: ScopeFilter): OutgoingResponse {
        if (statusCode !in 200..299) return this
        val items = try {
            Json.parseToJsonElement(body.decodeToString()) as? JsonArray
        } catch (e: SerializationException) {
            null
        } ?: throw DigdirException("Digdir answered $statusCode with something other than a JSON list")
        val kept = items.filter { item ->
            val value = (item as? JsonObject)?.get(field) as? JsonPrimitive
            value != null && value.isString && filter.allows(value.content)
        }
        return OutgoingResponse(statusCode, contentType, Json.encodeToString(JsonArray(kept)).toByteArray())
    }

    internal companion object {
        const val SCOPES_PATH = "/api/v1/scopes"

        const val SCOPE_ACCESS_PATH = "/api/v1/scopes/access"

        val ORGANIZATION_NUMBER = Regex("[0-9]{9}")
    }
}
