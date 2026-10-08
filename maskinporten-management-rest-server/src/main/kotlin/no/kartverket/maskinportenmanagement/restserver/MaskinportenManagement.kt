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
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import no.kartverket.maskinportenmanagement.client.http.JavaDigdirHttpClient
import java.net.http.HttpClient
import java.time.Duration

fun Application.configureMaskinportenManagement(client: MaskinportenManagementClient = clientFromConfig()) {
    install(DI)
    dependencies.provide<MaskinportenManagementClient> { client }
}

private fun Application.clientFromConfig(): MaskinportenManagementClient = MaskinportenManagementClient(
    baseUrl = environment.config.url("digdir.baseUrl"),
    httpClient = JavaDigdirHttpClient(
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        REQUEST_TIMEOUT,
    ),
)

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

internal suspend fun ApplicationCall.respondFromDigdir(response: DigdirHttpResponse) {
    respondBytes(
        bytes = response.body,
        contentType = response.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() },
        status = HttpStatusCode.fromValue(response.statusCode),
    )
}
