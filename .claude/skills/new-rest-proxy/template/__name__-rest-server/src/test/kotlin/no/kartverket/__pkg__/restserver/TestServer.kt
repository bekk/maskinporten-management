package no.kartverket.__pkg__.restserver

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.kartverket.__pkg__.client.__Name__Client
import no.kartverket.__pkg__.client.auth.AccessToken
import no.kartverket.__pkg__.client.auth.AccessTokenProvider
import no.kartverket.__pkg__.client.http.OutgoingHttpClient
import no.kartverket.__pkg__.restserver.models.ErrorResponse
import java.net.URI

/** The server as main() starts it, but calling [upstream] instead of __Upstream__. */
internal fun testServer(
    upstream: OutgoingHttpClient,
    tokenProvider: AccessTokenProvider = AccessTokenProvider { AccessToken.of("test-token") },
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application {
        configureSerialization()
        configureErrorHandling()
        configure__Name__(__Name__Client(URI("https://upstream.test"), upstream, tokenProvider))
        configureRouting()
    }
    block()
}

internal suspend fun HttpResponse.errorResponse(): ErrorResponse =
    Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())
