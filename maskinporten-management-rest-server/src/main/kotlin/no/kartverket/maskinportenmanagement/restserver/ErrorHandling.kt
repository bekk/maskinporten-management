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
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<JsonConvertException> { call, cause -> call.respondMalformedBody(cause) }
        exception<ContentTransformationException> { call, cause -> call.respondMalformedBody(cause) }
        exception<BadRequestException> { call, cause -> call.respondMalformedBody(cause) }
        exception<InvalidRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message!!, ErrorCode.INVALID_REQUEST))
        }
        exception<DigdirException> { call, cause ->
            call.application.log.error("Call to Digdir failed", cause)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("The call to Digdir failed", ErrorCode.UPSTREAM_ERROR))
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

private suspend fun ApplicationCall.respondMalformedBody(cause: Throwable) {
    application.log.warn("Malformed request body", cause)
    respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request body", ErrorCode.MALFORMED_BODY))
}
