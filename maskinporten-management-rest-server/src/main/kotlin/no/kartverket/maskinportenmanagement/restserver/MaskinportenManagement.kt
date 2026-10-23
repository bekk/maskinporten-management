package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.auth.MaskinportenTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ClientCertificate
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.kms.LocalKms
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

internal fun Application.maskinportenManagementClient(config: AppConfig): MaskinportenManagementClient {
    val httpClient = httpClient()
    val kms = config.digdirAdminCloudKmsKeyPath?.let(::CloudKms) ?: LocalKms.fromPem(checkNotNull(config.digdirAdminLocalKey))
    val tokenProvider = MaskinportenTokenProvider(
        wellKnownUrl = config.digdirEnvironment.maskinportenWellKnownUrl,
        clientId = config.maskinportenClientId,
        scopes = DIGDIR_SCOPES,
        kms = kms,
        certificateChainPem = config.digdirAdminCertChain,
        httpClient = httpClient,
    )
    monitor.subscribe(ApplicationStopped) {
        tokenProvider.close()
        (kms as? AutoCloseable)?.close()
    }
    return MaskinportenManagementClient(config.digdirEnvironment.digdirBaseUrl, httpClient, tokenProvider)
}

// Digdir's API requires idporten:scopes.read to read scope access and idporten:scopes.write to change it
private val DIGDIR_SCOPES = listOf(Scope.of("idporten:scopes.read"), Scope.of("idporten:scopes.write"))

internal fun Application.externalFiltering(config: AppConfig): ExternalFiltering {
    val url = config.externalFilteringUrl
    if (url == null) {
        log.warn("External filtering is off")
        return ExternalFiltering(null)
    }
    return ExternalFiltering(ExternalFilteringClient(url, httpClient()), config.externalFilteringCacheDuration)
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

class ExternalFiltering(
    private val client: ExternalFilteringClient?,
    cacheDuration: Duration = Duration.ZERO,
    clock: Clock = Clock.systemUTC(),
) {
    private val cache = ScopeFilterCache(cacheDuration, clock)

    val isOn: Boolean get() = client != null

    suspend fun filterFor(certificate: String?): ScopeFilter? {
        val client = client ?: return null
        checkNotNull(certificate)
        cache.get(certificate)?.let { return it }
        return client.filterFor(ClientCertificate.of(certificate)).also { cache.put(certificate, it) }
    }
}

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
