package no.kartverket.__pkg__.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import no.kartverket.__pkg__.client.__Name__Client
import no.kartverket.__pkg__.client.__Upstream__Environment
import no.kartverket.__pkg__.client.auth.AccessTokenProvider
import no.kartverket.__pkg__.client.http.JavaOutgoingHttpClient
import no.kartverket.__pkg__.client.http.OutgoingHttpClient
import java.net.http.HttpClient
import java.time.Duration

/** Tests pass their own [client], with a fake HTTP client and token. */
fun Application.configure__Name__(client: __Name__Client = clientFromConfig()) {
    install(DI)
    dependencies.provide<__Name__Client> { client }
}

private fun Application.clientFromConfig(): __Name__Client {
    val upstream = environment.config.enum<__Upstream__Environment>("__upstream__.environment")
    return __Name__Client(upstream.baseUrl, httpClient(), tokenProviderFromConfig())
}

// TODO: How this server gets a token for __Upstream__, for example a Maskinporten JWT grant. Put the provider in the
//  client's auth package, read its settings here with required(...), and add them to application.yaml and .env.example.
private fun Application.tokenProviderFromConfig(): AccessTokenProvider =
    TODO("No token provider for __Upstream__ yet: write tokenProviderFromConfig() in __Name__.kt")

// TODO: Logging of outgoing calls wraps this client in an OutgoingHttpClient of its own, once the team has settled how
//  we log. It must never log headers or bodies: they hold tokens.
private fun httpClient(): OutgoingHttpClient = JavaOutgoingHttpClient(
    HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    REQUEST_TIMEOUT,
)

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
