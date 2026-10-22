package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.queryString
import io.ktor.server.request.receive
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.ScopeJson
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.client.validation.ValidationException
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import kotlin.properties.ReadOnlyProperty

internal class InvalidRequestException(val errors: List<FieldError>) : RuntimeException(errors.joinToString("; ") { it.message })

internal class MalformedRequestException(message: String) : RuntimeException(message)

internal class RequestInput {
    private val errors = mutableListOf<FieldError>()

    fun invalid(field: String, code: ValidationCode, message: String) {
        if (errors.none { it.field == field }) errors += FieldError(field, code, message)
    }

    fun requireValid() {
        if (errors.isNotEmpty()) throw InvalidRequestException(errors.toList())
    }

    fun <T : Any> required(value: T?): ReadOnlyProperty<Any?, T> = ReadOnlyProperty { _, _ ->
        requireValid()
        checkNotNull(value)
    }

    fun <T> optional(value: T?): ReadOnlyProperty<Any?, T?> = ReadOnlyProperty { _, _ ->
        requireValid()
        value
    }
}

// Read from the raw query, so we act on the same values as the access check in front of the server: Netty drops
// parameters past the 1024th and reads "=scope=x" as scope, which other parsers do not
internal fun RequestInput.queryParameters(query: String, vararg names: String): Map<String, String> {
    val parameters = if (query.isEmpty()) emptyList() else query.split('&')
    val raw = parameters.map { parameter ->
        val parts = parameter.split('=', limit = 2)
        if (parts.size != 2 || parts[0].isEmpty()) throw MalformedRequestException(onlyThese(names))
        parts[0] to parts[1]
    }
    val values = mutableMapOf<String, String>()
    for ((name, sent) in raw.groupBy({ it.first }, { it.second })) {
        when {
            name !in names -> invalid(name, ValidationCode.NOT_ALLOWED, "$name is not allowed")

            sent.size > 1 -> invalid(name, ValidationCode.NOT_ALLOWED, "$name must be sent once")

            else -> try {
                values[name] = sent.single().decodeURLQueryComponent(plusIsSpace = true)
            } catch (e: URLDecodeException) {
                invalid(name, ValidationCode.INVALID_FORMAT, "$name is not URL-encoded correctly")
            }
        }
    }
    return values
}

private fun onlyThese(names: Array<out String>): String = when (names.size) {
    0 -> "The query must be empty"
    1 -> "The query must contain only the parameter ${names.single()}, sent once"
    else -> "The query must contain only the parameters ${names.dropLast(1).joinToString()} and ${names.last()}, each sent once"
}

// A query the server ignores could still be read by the access check in front of us, as a different scope
internal fun RequestInput.noQuery(call: ApplicationCall) {
    queryParameters(call.request.queryString())
}

internal fun RequestInput.scopeQueryParameter(call: ApplicationCall): ReadOnlyProperty<Any?, Scope> =
    scope("scope", queryParameters(call.request.queryString(), "scope")["scope"])

internal fun RequestInput.organizationNumberQueryParameter(
    call: ApplicationCall,
    name: String,
): ReadOnlyProperty<Any?, OrganizationNumber> = organizationNumber(name, queryParameters(call.request.queryString(), name)[name])

internal fun RequestInput.scope(name: String, value: String?): ReadOnlyProperty<Any?, Scope> =
    required(parse(name, value, "must not contain whitespace", Scope::of))

internal fun RequestInput.organizationNumber(name: String, value: String?): ReadOnlyProperty<Any?, OrganizationNumber> =
    required(parse(name, value, "must be an organisation number of 9 digits", OrganizationNumber::of))

internal fun RequestInput.wholeNumber(name: String, value: String?, min: Int): ReadOnlyProperty<Any?, Int?> {
    val number = value?.toIntOrNull()?.takeIf { it >= min }
    if (value != null && number == null) {
        invalid(name, ValidationCode.INVALID_FORMAT, "$name must be a whole number of $min or more")
    }
    return optional(number)
}

private fun <T : Any> RequestInput.parse(name: String, value: String?, format: String, parse: (String) -> T): T? {
    if (value.isNullOrBlank()) {
        invalid(name, ValidationCode.MISSING, "$name is required")
        return null
    }
    return try {
        parse(value)
    } catch (e: IllegalArgumentException) {
        invalid(name, ValidationCode.INVALID_FORMAT, "$name $format")
        null
    }
}

internal fun RequestInput.clientCertificate(call: ApplicationCall, required: Boolean): ReadOnlyProperty<Any?, String?> {
    val certificate = call.request.headers[CLIENT_CERTIFICATE_HEADER]?.takeIf { it.isNotBlank() }
    if (required && certificate == null) {
        invalid(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")
    }
    return optional(certificate)
}

internal suspend fun RequestInput.scopeJson(call: ApplicationCall): ReadOnlyProperty<Any?, ScopeJson> {
    val scope = try {
        ScopeJson.of(call.jsonBody())
    } catch (e: ValidationException) {
        e.errors.forEach { invalid(it.field, it.code, it.message) }
        null
    } catch (e: IllegalArgumentException) {
        throw MalformedRequestException(e.message!!)
    }
    return required(scope)
}

// Strictly UTF-8, whatever charset the Content-Type names, so Digdir gets the bytes the access check in front of us saw
internal suspend fun ApplicationCall.jsonBody(): String {
    requireJsonContentType()
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(receive<ByteArray>())).toString()
    } catch (e: CharacterCodingException) {
        throw MalformedRequestException("The body must be UTF-8")
    }
}

private fun ApplicationCall.requireJsonContentType() {
    val contentType = try {
        request.contentType()
    } catch (e: BadContentTypeFormatException) {
        null
    }
    if (contentType?.withoutParameters() != ContentType.Application.Json) {
        throw MalformedRequestException("The body must be JSON, sent with Content-Type application/json")
    }
}
