package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
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

// Sent twice, OPA could check one value while Digdir gets the other
internal fun ApplicationCall.singleQueryParameter(name: String): String {
    val values = request.queryParameters.getAll(name).orEmpty()
    if (values.size > 1) throw InvalidRequestException("Query parameter $name must be sent only once")
    return values.singleOrNull()?.takeIf { it.isNotBlank() }
        ?: throw InvalidRequestException("Query parameter $name is required")
}

internal suspend fun ApplicationCall.respondFromDigdir(response: DigdirHttpResponse) {
    respondBytes(
        bytes = response.body,
        contentType = response.contentType?.let(ContentType::parse),
        status = HttpStatusCode.fromValue(response.statusCode),
    )
}
