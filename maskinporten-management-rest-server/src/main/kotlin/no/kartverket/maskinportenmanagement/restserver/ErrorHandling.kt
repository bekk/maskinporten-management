package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringException
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("The URL is not encoded correctly", ErrorCode.INVALID_REQUEST))
        }
        exception<InvalidRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message!!, ErrorCode.INVALID_REQUEST))
        }
        exception<DigdirException> { call, cause ->
            call.application.log.error("Call to Digdir failed", cause)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("The call to Digdir failed", ErrorCode.UPSTREAM_ERROR))
        }
        exception<ExternalFilteringException> { call, cause ->
            call.application.log.error("Call to the filtering service failed", cause)
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR),
            )
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
