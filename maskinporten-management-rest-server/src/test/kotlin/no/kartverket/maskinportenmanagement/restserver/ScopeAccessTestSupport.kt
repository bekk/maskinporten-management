package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpClient
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpRequest
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpResponse
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessResponse

internal const val SAMPLE_SCOPE = "kartverk:matrikkel.read"

internal const val SAMPLE_ACCESS_JSON =
    """{"scope":"$SAMPLE_SCOPE","owner_orgno":"971040238","owner_organization_name":"Statens kartverk",""" +
        """"consumer_orgno":"971032081","consumer_organization_name":"Statens vegvesen","state":"APPROVED",""" +
        """"active":true,"created":"2026-03-01T08:00:00Z","last_updated":"2026-03-02T08:00:00Z"}"""

internal suspend fun ApplicationTestBuilder.postScopeAccess(
    body: String = """{"scope":"$SAMPLE_SCOPE"}""",
    json: Boolean = true,
): HttpResponse = client.post("/scopeaccess") {
    if (json) contentType(ContentType.Application.Json)
    setBody(body)
}

internal suspend fun HttpResponse.scopeAccessResponse(): ScopeAccessResponse =
    Json.decodeFromString(ScopeAccessResponse.serializer(), bodyAsText())

internal suspend fun HttpResponse.errorResponse(): ErrorResponse =
    Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())

internal fun scopeAccessTest(
    statusCode: Int = 200,
    body: String = "[$SAMPLE_ACCESS_JSON]",
    failure: Exception? = null,
    requests: MutableList<ManagementHttpRequest> = mutableListOf(),
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    val maskinporten = ManagementHttpClient { request ->
        requests += request
        if (failure != null) throw failure
        when (request.url.path) {
            "/api/v1/scopes/access" -> ManagementHttpResponse(statusCode, body)
            else -> error("unexpected call to ${request.url}")
        }
    }
    application {
        configureSerialization()
        configureErrorHandling()
        configureMaskinportenManagement(MaskinportenManagementClient("https://maskinporten.test/api/v1", maskinporten))
        configureRouting()
    }
    block()
}
