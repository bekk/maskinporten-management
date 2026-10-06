package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import no.kartverket.maskinportenmanagement.client.exception.MaskinportenApiException
import no.kartverket.maskinportenmanagement.client.exception.MaskinportenManagementException
import no.kartverket.maskinportenmanagement.client.exception.ValidationException
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.FieldError

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<JsonConvertException> { call, cause -> call.respondUnreadableBody(cause) }
        exception<ContentTransformationException> { call, cause -> call.respondUnreadableBody(cause) }
        exception<BadRequestException> { call, cause -> call.respondUnreadableBody(cause) }
        exception<MaskinportenManagementException> { call, cause ->
            call.application.log.error("Call to Maskinporten failed: statusCode=${cause.statusCode}", cause)
            call.respondUpstream(cause)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error", ErrorCode.INTERNAL_ERROR),
            )
        }
    }
}

private suspend fun ApplicationCall.respondValidationFailed(cause: ValidationException) {
    respond(
        HttpStatusCode.BadRequest,
        ErrorResponse(
            error = "Validation failed",
            code = ErrorCode.VALIDATION_ERROR,
            errors = cause.errors.map { FieldError(it.field, it.code, it.message) },
        ),
    )
}

private suspend fun ApplicationCall.respondUnreadableBody(cause: Throwable) {
    val rejected = generateSequence(cause) { it.cause }.filterIsInstance<ValidationException>().firstOrNull()
    if (rejected != null) respondValidationFailed(rejected) else respondMalformedBody(cause)
}

private suspend fun ApplicationCall.respondMalformedBody(cause: Throwable) {
    application.log.warn("Malformed request body", cause)
    respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request body", ErrorCode.MALFORMED_BODY))
}

private suspend fun ApplicationCall.respondUpstream(cause: MaskinportenManagementException) {
    if (cause is MaskinportenApiException && cause.statusCode == 400) {
        respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Maskinporten rejected the request", ErrorCode.UPSTREAM_REJECTED),
        )
    } else {
        respond(HttpStatusCode.BadGateway, ErrorResponse("The call to Maskinporten failed", ErrorCode.UPSTREAM_ERROR))
    }
}
