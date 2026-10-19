package no.kartverket.__pkg__.restserver

import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import no.kartverket.__pkg__.client.exception.TokenException
import no.kartverket.__pkg__.client.exception.__Upstream__Exception
import no.kartverket.__pkg__.client.validation.ValidationException
import no.kartverket.__pkg__.restserver.models.ErrorCode
import no.kartverket.__pkg__.restserver.models.ErrorResponse
import no.kartverket.__pkg__.restserver.models.FieldError

/**
 * Every error answer is an [ErrorResponse]. __Upstream__'s own error answers never reach the caller: a 4xx that is about
 * the caller's request keeps its status, with [ErrorCode.UPSTREAM_REJECTED]. Everything else from __Upstream__ is our
 * problem, not the caller's, and gives 502 with [ErrorCode.UPSTREAM_ERROR].
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<JsonConvertException> { call, cause -> call.respondMalformed(cause) }
        exception<ContentTransformationException> { call, cause -> call.respondMalformed(cause) }
        exception<BadRequestException> { call, cause -> call.respondMalformed(cause) }
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Validation failed",
                    code = ErrorCode.VALIDATION_ERROR,
                    errors = cause.errors.map { FieldError(it.field, it.code, it.message) },
                ),
            )
        }
        exception<__Upstream__Exception> { call, cause ->
            val status = cause.statusCode
            if (status != null && status in 400..499 && status !in OUR_PROBLEM) {
                call.application.log.info("__Upstream__ rejected the request: $cause")
                call.respond(
                    HttpStatusCode.fromValue(status),
                    ErrorResponse("__Upstream__ rejected the request", ErrorCode.UPSTREAM_REJECTED),
                )
            } else {
                call.application.log.error("Call to __Upstream__ failed", cause)
                call.respondUpstreamError()
            }
        }
        exception<TokenException> { call, cause ->
            call.application.log.error("Getting a token for __Upstream__ failed", cause)
            call.respondUpstreamError()
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

// Statuses that are about our client or token at __Upstream__, not about the caller's request
private val OUR_PROBLEM = setOf(401, 403, 407, 408, 429)

private suspend fun ApplicationCall.respondMalformed(cause: Throwable) {
    val error = if (isBrokenUrl(request.uri)) {
        "The URL is not encoded correctly"
    } else {
        val causes = generateSequence(cause) { it.cause }.joinToString(" caused by ") { it.javaClass.name }
        application.log.info("Request refused as malformed: $causes")
        "The request could not be read"
    }
    respond(HttpStatusCode.BadRequest, ErrorResponse(error, ErrorCode.MALFORMED_REQUEST))
}

private suspend fun ApplicationCall.respondUpstreamError() {
    respond(HttpStatusCode.BadGateway, ErrorResponse("The call to __Upstream__ failed", ErrorCode.UPSTREAM_ERROR))
}

internal fun isBrokenUrl(uri: String): Boolean = try {
    uri.decodeURLQueryComponent()
    false
} catch (e: URLDecodeException) {
    true
}
