package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.DigdirErrorResponseException
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringException
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.FieldError

internal fun StatusPagesConfig.errorResponses() {
    exception<BadRequestException> { call, cause ->
        val detail = if (isBrokenUrl(call.request.uri)) {
            "The URL is not encoded correctly"
        } else {
            call.application.log.info("Request refused by Ktor: $cause")
            "The request could not be read"
        }
        call.respondProblem(HttpStatusCode.BadRequest, detail, ErrorCode.MALFORMED_REQUEST)
    }
    exception<MalformedRequestException> { call, cause ->
        call.respondProblem(HttpStatusCode.BadRequest, cause.message!!, ErrorCode.MALFORMED_REQUEST)
    }
    exception<InvalidRequestException> { call, cause ->
        call.respondProblem(HttpStatusCode.BadRequest, "Validation failed", ErrorCode.VALIDATION_ERROR, cause.errors)
    }
    exception<DigdirException> { call, cause ->
        call.application.log.error("Call to Digdir failed", cause)
        call.respondProblem(HttpStatusCode.BadGateway, "The call to Digdir failed", ErrorCode.UPSTREAM_ERROR)
    }
    exception<DigdirErrorResponseException> { call, cause ->
        call.respondBytes(
            bytes = cause.body,
            contentType = cause.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() },
            status = HttpStatusCode.fromValue(cause.statusCode),
        )
    }
    exception<ExternalFilteringException> { call, cause ->
        call.application.log.error("Call to the filtering service failed", cause)
        call.respondProblem(HttpStatusCode.BadGateway, "The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR)
    }
    exception<CloudKmsException> { call, cause ->
        call.application.log.error("Call to Cloud KMS failed", cause)
        call.respondProblem(HttpStatusCode.BadGateway, "The call to Cloud KMS failed", ErrorCode.UPSTREAM_ERROR)
    }
    exception<Throwable> { call, cause ->
        call.application.log.error("Unhandled exception", cause)
        call.respondProblem(HttpStatusCode.InternalServerError, "Internal server error", ErrorCode.INTERNAL_ERROR)
    }
}

private suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    detail: String,
    code: ErrorCode,
    errors: List<FieldError>? = null,
) = respondText(
    Json.encodeToString(ErrorResponse.serializer(), ErrorResponse.of(status, detail, code, errors)),
    ContentType.Application.ProblemJson,
    status,
)

internal fun isBrokenUrl(uri: String): Boolean = try {
    uri.decodeURLQueryComponent()
    false
} catch (e: URLDecodeException) {
    true
}
