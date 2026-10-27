package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.ProblemType
import java.net.URI

internal const val CLIENT_CERTIFICATE =
    "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

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
    application { module(testClient(digdir), externalFiltering) }
    block()
}

internal fun testClient(digdir: OutgoingHttpClient = OutgoingHttpClient { error("unexpected call to ${it.url}") }) =
    MaskinportenManagementClient(URI("https://digdir.test"), digdir) { AccessToken.of("test-token") }

internal suspend fun HttpResponse.errorResponse(): ErrorResponse =
    Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())

internal fun validationFailed(vararg errors: FieldError) =
    ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = errors.toList())

internal fun malformedRequest(detail: String) = ErrorResponse.of(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, detail)

internal fun upstreamError(detail: String) = ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, detail)

@Serializable
private data class FilteringInput(val headers: Map<String, String>)

@Serializable
private data class FilteringRequestBody(val input: FilteringInput)

// Reads the certificate the app relayed to the filtering service, out of the request body it sent it in.
internal fun OutgoingRequest.certificateSentToFiltering(): String =
    Json.decodeFromString(FilteringRequestBody.serializer(), body!!.decodeToString()).input.headers.getValue("x-forwarded-client-cert")
