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
        send("GET", "$SCOPE_ACCESS_PATH?scope=${encode(scope)}")

    public suspend fun grantScopeAccess(consumerOrgno: String, scope: String): DigdirHttpResponse =
        send("PUT", "$SCOPE_ACCESS_PATH/${pathSegment(consumerOrgno)}?scope=${encode(scope)}")

    public suspend fun removeScopeAccess(consumerOrgno: String, scope: String): DigdirHttpResponse =
        send("DELETE", "$SCOPE_ACCESS_PATH/${pathSegment(consumerOrgno)}?scope=${encode(scope)}")

    // In the path, ".." would turn DELETE .../access/.. into DELETE /api/v1/scopes: deleting the scope itself
    private fun pathSegment(consumerOrgno: String): String {
        require(ORGANIZATION_NUMBER.matches(consumerOrgno)) {
            "consumerOrgno must be an organisation number of 9 digits, but was \"$consumerOrgno\""
        }
        return consumerOrgno
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private suspend fun send(method: String, pathAndQuery: String): DigdirHttpResponse {
        val request = DigdirHttpRequest(
            method = method,
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

        val ORGANIZATION_NUMBER = Regex("[0-9]{9}")
    }
}
