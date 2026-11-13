package no.kartverket.maskinportenmanagement.restserver.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.kms.LocalKms
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.restserver.kms.CloudKms
import no.kartverket.maskinportenmanagement.restserver.observability.LoggingOutgoingHttpClient
import java.net.http.HttpClient
import java.time.Duration

internal fun Application.maskinportenManagementClient(config: DigdirConfig): MaskinportenManagementClient {
    val kms = config.digdirAdminCloudKmsKeyPath?.let(::CloudKms) ?: LocalKms.fromPem(checkNotNull(config.digdirAdminLocalKey))
    val client = MaskinportenManagementClient(
        baseUrl = config.digdirEnvironment.digdirBaseUrl,
        maskinportenWellKnownUrl = config.digdirEnvironment.maskinportenWellKnownUrl,
        clientId = config.maskinportenClientId,
        kms = kms,
        certificateChainPem = config.digdirAdminCertChain,
        httpClient = httpClient(),
    )
    monitor.subscribe(ApplicationStopped) {
        client.close()
        (kms as? AutoCloseable)?.close()
    }
    return client
}

internal fun Application.externalFiltering(config: AppConfig, httpClient: OutgoingHttpClient = httpClient()): ExternalFiltering {
    val url = config.externalFilteringUrl
    if (url == null) {
        log.warn("External filtering is off")
        return ExternalFiltering(null)
    }
    return ExternalFiltering(ExternalFilteringClient(url, httpClient), config.externalFilteringCacheDuration)
}

private fun httpClient(): OutgoingHttpClient = LoggingOutgoingHttpClient(
    JavaOutgoingHttpClient(
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        REQUEST_TIMEOUT,
    ),
)

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
