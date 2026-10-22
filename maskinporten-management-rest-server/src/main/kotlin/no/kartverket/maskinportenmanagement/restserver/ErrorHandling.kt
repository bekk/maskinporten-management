package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.http.charset
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        val answer = cause.text()
        if (cause.statusCode in 400..499) {
            call.application.log.info("Digdir rejected the request with HTTP ${cause.statusCode}: $answer")
            val detail = explanation(answer) ?: "Digdir rejected the request"
            call.respondProblem(HttpStatusCode.fromValue(cause.statusCode), detail, ErrorCode.UPSTREAM_REJECTED)
        } else {
            call.application.log.error("Digdir answered HTTP ${cause.statusCode}: $answer")
            call.respondProblem(HttpStatusCode.BadGateway, "The call to Digdir failed", ErrorCode.UPSTREAM_ERROR)
        }
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
    status(HttpStatusCode.NotFound) { call, status ->
        call.respondProblem(status, "There is no endpoint at this path", ErrorCode.NOT_FOUND)
    }
    status(HttpStatusCode.MethodNotAllowed) { call, status ->
        call.respondProblem(status, "The endpoint at this path does not accept this method", ErrorCode.METHOD_NOT_ALLOWED)
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

private fun DigdirErrorResponseException.text(): String {
    val charset = contentType?.let { runCatching { ContentType.parse(it).charset() }.getOrNull() }
    return body.toString(charset ?: Charsets.UTF_8)
}

private fun explanation(answer: String): String? {
    val fields = runCatching { Json.parseToJsonElement(answer) }.getOrNull() as? JsonObject ?: return null
    return listOf("error_description", "error").firstNotNullOfOrNull { name ->
        (fields[name] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
    }
}

internal fun isBrokenUrl(uri: String): Boolean = try {
    uri.decodeURLQueryComponent()
    false
} catch (e: URLDecodeException) {
    true
}
