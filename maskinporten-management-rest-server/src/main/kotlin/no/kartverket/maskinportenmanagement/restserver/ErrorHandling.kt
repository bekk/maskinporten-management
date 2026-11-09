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
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.DigdirErrorResponseException
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFilteringException
import no.kartverket.maskinportenmanagement.restserver.kms.CloudKmsException
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.ProblemType

internal fun StatusPagesConfig.errorResponses() {
    exception<BadRequestException> { call, cause ->
        val detail = if (isBrokenUrl(call.request.uri)) {
            "The URL is not encoded correctly"
        } else {
            call.application.log.info("Request refused by Ktor: $cause")
            "The request could not be read"
        }
        call.respondProblem(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, detail)
    }
    exception<MalformedRequestException> { call, cause ->
        call.respondProblem(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, cause.message!!)
    }
    exception<InvalidRequestException> { call, cause ->
        call.respondProblem(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = cause.errors)
    }
    exception<DigdirException> { call, cause ->
        call.application.log.error("Call to Digdir failed", cause)
        call.respondProblem(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Digdir failed")
    }
    exception<DigdirErrorResponseException> { call, cause ->
        if (cause.statusCode in 400..499) {
            call.application.log.info("Digdir rejected the request with HTTP ${cause.statusCode}: ${cause.responseBody}")
            val detail = cause.explanation ?: "Digdir rejected the request"
            call.respondProblem(ProblemType.UPSTREAM_REJECTED, HttpStatusCode.fromValue(cause.statusCode), detail)
        } else {
            call.application.log.error("Digdir answered HTTP ${cause.statusCode}: ${cause.responseBody}")
            call.respondProblem(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Digdir failed")
        }
    }
    exception<ExternalFilteringException> { call, cause ->
        call.application.log.error("Call to the filtering service failed", cause)
        call.respondProblem(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to the filtering service failed")
    }
    exception<CloudKmsException> { call, cause ->
        call.application.log.error("Call to Cloud KMS failed", cause)
        call.respondProblem(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Cloud KMS failed")
    }
    exception<Throwable> { call, cause ->
        call.application.log.error("Unhandled exception", cause)
        call.respondProblem(ProblemType.INTERNAL_ERROR, HttpStatusCode.InternalServerError)
    }
    status(HttpStatusCode.NotFound) { call, status ->
        call.respondProblem(ProblemType.NOT_FOUND, status, "There is no endpoint at this path")
    }
    status(HttpStatusCode.MethodNotAllowed) { call, status ->
        call.respondProblem(ProblemType.METHOD_NOT_ALLOWED, status, "The endpoint at this path does not accept this method")
    }
}

private suspend fun ApplicationCall.respondProblem(
    type: ProblemType,
    status: HttpStatusCode,
    detail: String? = null,
    errors: List<FieldError>? = null,
) = respondText(
    Json.encodeToString(ErrorResponse.serializer(), ErrorResponse.of(type, status, detail, errors)),
    ContentType.Application.ProblemJson,
    status,
)

internal fun isBrokenUrl(uri: String): Boolean = try {
    uri.decodeURLQueryComponent()
    false
} catch (e: URLDecodeException) {
    true
}
