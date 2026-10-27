package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import no.kartverket.maskinportenmanagement.client.DigdirErrorResponseException
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringException
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse

internal fun StatusPagesConfig.errorResponses() {
    exception<BadRequestException> { call, cause ->
        val error = if (isBrokenUrl(call.request.uri)) {
            "The URL is not encoded correctly"
        } else {
            call.application.log.info("Request refused by Ktor: $cause")
            "The request could not be read"
        }
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(error, ErrorCode.INVALID_REQUEST))
    }
    exception<InvalidRequestException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message!!, ErrorCode.INVALID_REQUEST))
    }
    exception<DigdirException> { call, cause ->
        call.application.log.error("Call to Digdir failed", cause)
        call.respond(HttpStatusCode.BadGateway, ErrorResponse("The call to Digdir failed", ErrorCode.UPSTREAM_ERROR))
    }
    exception<DigdirErrorResponseException> { call, cause ->
        if (cause.statusCode >= 500) {
            call.application.log.error("Digdir answered HTTP ${cause.statusCode}: ${cause.body.decodeToString()}")
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("The call to Digdir failed", ErrorCode.UPSTREAM_ERROR))
        } else {
            call.respondBytes(
                bytes = cause.body,
                contentType = cause.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() },
                status = HttpStatusCode.fromValue(cause.statusCode),
            )
        }
    }
    exception<ExternalFilteringException> { call, cause ->
        call.application.log.error("Call to the filtering service failed", cause)
        call.respond(
            HttpStatusCode.BadGateway,
            ErrorResponse("The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR),
        )
    }
    exception<CloudKmsException> { call, cause ->
        call.application.log.error("Call to Cloud KMS failed", cause)
        call.respond(HttpStatusCode.BadGateway, ErrorResponse("The call to Cloud KMS failed", ErrorCode.UPSTREAM_ERROR))
    }
    exception<Throwable> { call, cause ->
        call.application.log.error("Unhandled exception", cause)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse("Internal server error", ErrorCode.INTERNAL_ERROR),
        )
    }
}

internal fun isBrokenUrl(uri: String): Boolean = try {
    uri.decodeURLQueryComponent()
    false
} catch (e: URLDecodeException) {
    true
}
