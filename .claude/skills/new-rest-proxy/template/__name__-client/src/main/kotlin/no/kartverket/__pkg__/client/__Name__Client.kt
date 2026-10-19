package no.kartverket.__pkg__.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.kartverket.__pkg__.client.auth.AccessToken
import no.kartverket.__pkg__.client.auth.AccessTokenProvider
import no.kartverket.__pkg__.client.exception.TokenException
import no.kartverket.__pkg__.client.exception.__Upstream__Exception
import no.kartverket.__pkg__.client.http.HttpMethod
import no.kartverket.__pkg__.client.http.OutgoingHttpClient
import no.kartverket.__pkg__.client.http.OutgoingRequest
import no.kartverket.__pkg__.client.http.OutgoingResponse
import no.kartverket.__pkg__.client.validation.ValidationCode
import no.kartverket.__pkg__.client.validation.ValidationError
import no.kartverket.__pkg__.client.validation.ValidationException
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/**
 * Calls __Upstream__'s API. Input that cannot be right throws a [ValidationException] before __Upstream__ is called. A
 * failure to get an answer throws a [no.kartverket.__pkg__.client.exception.__Name__ClientException].
 */
public class __Name__Client(
    baseUrl: URI,
    private val httpClient: OutgoingHttpClient,
    private val accessTokenProvider: AccessTokenProvider,
) {
    private val baseUrl = baseUrl.toString().trimEnd('/')

    // TODO: Replace with __Upstream__'s real operations
    public suspend fun getExampleItem(id: String): ExampleItem {
        validateExampleItemId(id)
        return send(HttpMethod.GET, "$EXAMPLE_ITEMS_PATH/${encode(id)}").decode()
    }

    private suspend fun send(method: HttpMethod, pathAndQuery: String): OutgoingResponse {
        val token = gettingToken { accessTokenProvider.accessToken() }
        val request = OutgoingRequest(
            method = method,
            url = URI.create(baseUrl + pathAndQuery),
            headers = mapOf("Accept" to "application/json", "Authorization" to "Bearer ${token.value}"),
        )
        val response = calling__Upstream__ { httpClient.send(request) }
        val body = response.body.decodeToString()
        // Our token is wrong, not the caller's request
        if (response.statusCode == 401) {
            accessTokenProvider.refused(token)
            throw __Upstream__Exception("__Upstream__ refused our access token", response.statusCode, body)
        }
        if (response.statusCode !in 200..299) {
            throw __Upstream__Exception("__Upstream__ answered HTTP ${response.statusCode}", response.statusCode, body)
        }
        return response
    }

    private inline fun gettingToken(call: () -> AccessToken): AccessToken = try {
        call()
    } catch (e: IOException) {
        throw TokenException("Getting an access token for __Upstream__ failed: $e", cause = e)
    }

    private inline fun <T> calling__Upstream__(call: () -> T): T = try {
        call()
    } catch (e: IOException) {
        throw __Upstream__Exception("Call to __Upstream__ failed: $e", cause = e)
    }

    private inline fun <reified T> OutgoingResponse.decode(): T = try {
        json.decodeFromString<T>(body.decodeToString())
    } catch (e: SerializationException) {
        throw __Upstream__Exception(
            "__Upstream__ answered HTTP $statusCode with something this client cannot read: ${e.message}",
            statusCode,
            cause = e,
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    internal companion object {
        const val EXAMPLE_ITEMS_PATH = "/example-items"

        val EXAMPLE_ITEM_ID_FORMAT = Regex("^[A-Za-z0-9_-]{1,64}$")

        val json = Json { ignoreUnknownKeys = true }

        fun validateExampleItemId(id: String) {
            val error = when {
                id.isBlank() -> ValidationError("id", ValidationCode.MISSING, "id is required")

                !EXAMPLE_ITEM_ID_FORMAT.matches(id) -> ValidationError(
                    "id",
                    ValidationCode.INVALID_FORMAT,
                    "id must be 1 to 64 letters, digits, underscores or hyphens",
                )

                else -> return
            }
            throw ValidationException(listOf(error))
        }
    }
}
