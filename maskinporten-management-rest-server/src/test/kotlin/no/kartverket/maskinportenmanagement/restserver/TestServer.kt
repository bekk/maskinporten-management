package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import java.net.URI

internal const val CLIENT_CERTIFICATE =
    "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

internal fun json(status: Int, body: String) = OutgoingResponse(status, "application/json", body.toByteArray())

internal fun recording(requests: MutableList<OutgoingRequest>, answer: (OutgoingRequest) -> OutgoingResponse) =
    OutgoingHttpClient { request ->
        requests += request
        answer(request)
    }

internal fun testServer(
    digdir: OutgoingHttpClient,
    filtering: OutgoingHttpClient?,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testServer(digdir, ExternalFiltering(filtering?.let { ExternalFilteringClient(URI("https://filtering.test/scopes"), it) }), block)

internal fun testServer(
    digdir: OutgoingHttpClient,
    externalFiltering: ExternalFiltering,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application {
        configureSerialization()
        configureErrorHandling()
        configureMaskinportenManagement(
            MaskinportenManagementClient(URI("https://digdir.test"), digdir) { AccessToken.of("test-token") },
            externalFiltering,
        )
        configureRouting()
    }
    block()
}

internal suspend fun HttpResponse.errorResponse(): ErrorResponse =
    Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())
