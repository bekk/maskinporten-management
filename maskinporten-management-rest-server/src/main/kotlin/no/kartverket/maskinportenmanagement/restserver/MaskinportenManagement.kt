package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.http.JavaManagementHttpClient
import java.net.http.HttpClient
import java.time.Duration

fun Application.configureMaskinportenManagement(client: MaskinportenManagementClient = clientFromConfig()) {
    install(DI)
    dependencies.provide<MaskinportenManagementClient> { client }
}

private fun Application.clientFromConfig(): MaskinportenManagementClient =
    MaskinportenManagementClient(
        apiBaseUrl = environment.config.required("maskinporten.apiBaseUrl"),
        httpClient = JavaManagementHttpClient(
            HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
            REQUEST_TIMEOUT,
        ),
    )

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)
