package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.queryString
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingCall
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import no.kartverket.maskinportenmanagement.client.http.JavaDigdirHttpClient
import java.net.http.HttpClient
import java.time.Duration

fun Application.configureMaskinportenManagement(
    client: MaskinportenManagementClient = clientFromConfig(),
    externalFiltering: ExternalFiltering = externalFilteringFromConfig(),
) {
    install(DI)
    dependencies.provide<MaskinportenManagementClient> { client }
    dependencies.provide<ExternalFiltering> { externalFiltering }
}

private fun Application.clientFromConfig(): MaskinportenManagementClient =
    MaskinportenManagementClient(environment.config.url("digdir.baseUrl"), httpClient())

// On unless turned off, so a missing URL stops the server instead of showing every app all of Kartverket's scopes
private fun Application.externalFilteringFromConfig(): ExternalFiltering {
    if (!environment.config.boolean("externalFiltering.enabled", default = true)) return ExternalFiltering(null)
    return ExternalFiltering(ExternalFilteringClient(environment.config.url("externalFiltering.url"), httpClient()))
}

private fun httpClient() = JavaDigdirHttpClient(
    HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    REQUEST_TIMEOUT,
)

class ExternalFiltering(private val client: ExternalFilteringClient?) {
    suspend fun filterFor(call: ApplicationCall): ScopeFilter? {
        val client = client ?: return null
        val certificate = call.request.headers[CLIENT_CERTIFICATE_HEADER]?.takeIf { it.isNotBlank() }
            ?: throw InvalidRequestException("Header $CLIENT_CERTIFICATE_HEADER is required")
        return client.filterFor(certificate)
    }
}

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)

internal class InvalidRequestException(message: String) : RuntimeException(message)

internal fun ApplicationCall.onlyQueryParameter(name: String): String = onlyQueryParameter(request.queryString(), name)

// Read from the raw query, so OPA cannot see another value: Netty drops parameters past the 1024th and reads "=scope=x"
// as scope, which OPA's parser does not
internal fun onlyQueryParameter(query: String, name: String): String {
    if (query.isEmpty()) throw InvalidRequestException("Query parameter $name is required")
    val (key, raw) = query.split('=', limit = 2).takeIf { it.size == 2 && '&' !in query }
        ?: throw InvalidRequestException("The query must contain only the parameter $name, sent once")
    if (key != name) throw InvalidRequestException("The query must contain only the parameter $name, sent once")
    val value = try {
        raw.decodeURLQueryComponent(plusIsSpace = true)
    } catch (e: URLDecodeException) {
        throw InvalidRequestException("Query parameter $name is not URL-encoded correctly")
    }
    return value.takeIf { it.isNotBlank() } ?: throw InvalidRequestException("Query parameter $name is required")
}

internal fun RoutingCall.organizationNumberPathParameter(name: String): String {
    val value = pathParameters[name].orEmpty()
    if (!ORGANIZATION_NUMBER.matches(value)) {
        throw InvalidRequestException("Path parameter $name must be an organisation number of 9 digits")
    }
    return value
}

internal fun ApplicationCall.organizationNumberQueryParameter(name: String): String {
    val value = onlyQueryParameter(name)
    if (!ORGANIZATION_NUMBER.matches(value)) {
        throw InvalidRequestException("Query parameter $name must be an organisation number of 9 digits")
    }
    return value
}

private val ORGANIZATION_NUMBER = Regex("[0-9]{9}")

internal suspend fun ApplicationCall.respondFromDigdir(response: DigdirHttpResponse) {
    respondBytes(
        bytes = response.body,
        contentType = response.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() },
        status = HttpStatusCode.fromValue(response.statusCode),
    )
}
