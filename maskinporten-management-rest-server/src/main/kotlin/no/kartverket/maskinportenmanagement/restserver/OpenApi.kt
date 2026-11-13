package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.Headers
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.MediaType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Parameters
import io.ktor.openapi.Responses
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.kartverket.maskinportenmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.restserver.problem.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.problem.FieldError
import no.kartverket.maskinportenmanagement.restserver.problem.ProblemType
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode

private val openApiJson = Json { prettyPrint = true }

internal val exampleJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

private val apiInfo = OpenApiInfo(
    title = "Maskinporten Management REST API",
    version = "v0",
    description = """
        Manages Kartverket's Maskinporten scopes through Digdir's API. It lists, creates, updates and deletes the
        scopes. It also lists, gives and removes organisations' access to them, and shows the changes to that access.

        Filtering is set for each deployment of this API. When it is on, `GET /api/scopes` and
        `GET /api/scopeaccess/scopes` only show the scopes the calling app has access to, and need the
        `X-Forwarded-Client-Cert` header. When it is off, nothing is left out. Answers use Digdir's field names, but
        only hold the fields described here: a field Digdir adds later is left out.

        When Digdir rejects a request, this API answers with Digdir's status and explanation, in its own error format.
        The exception is a `401`: Digdir refused this API's token, not your request, so this API answers `502`.
    """.trimIndent(),
)

fun Application.openApiSpec(): String =
    openApiJson.encodeToString(OpenApiDoc(info = apiInfo) + plugin(RoutingRoot).getAllRoutes())

internal fun Parameters.Builder.scope(onlyParameter: Boolean = true) {
    query("scope") {
        required = true
        description = if (onlyParameter) {
            "The scope, for example `kartverk:matrikkel.read`. It must be the only query parameter, sent once."
        } else {
            "The scope, for example `kartverk:matrikkel.read`."
        }
        schema = JsonSchema(type = JsonType.STRING)
    }
}

internal fun Parameters.Builder.orgnr() {
    query("orgnr") {
        required = true
        description = "The consumer's organisation number: 9 digits, for example `311718371`. It must be the only " +
            "query parameter, sent once."
        schema = JsonSchema(type = JsonType.STRING, pattern = "^[0-9]{9}$")
    }
}

internal fun Parameters.Builder.clientCertificate() {
    header(CLIENT_CERTIFICATE_HEADER) {
        description = "Says which app is calling. Istio usually sets it on every call, so the app does not. This " +
            "API requires it when filtering is on."
        schema = JsonSchema(type = JsonType.STRING)
    }
}

internal fun Parameters.Builder.requestId() {
    header(HttpHeaders.XRequestId) {
        description = "An id to find this call by in this API's logs. Up to 128 ASCII letters, digits, `-`, `_`, " +
            "`.` and `:`. Without it, or with any other value, this API makes a new one. Every response has the id " +
            "in its `X-Request-Id` header."
        schema = JsonSchema(type = JsonType.STRING)
        example = GenericElement("3f2b8c1e-7d4a-4f0e-9b6a-2c5d8e1f4a7b")
    }
}

internal fun Headers.Builder.requestId() = header(HttpHeaders.XRequestId) {
    description = "The id of this call in this API's logs: the `X-Request-Id` you sent, or a new one."
    required = true
    schema = JsonSchema(type = JsonType.STRING)
}

internal fun Parameters.Builder.consumerOrgno() {
    path("consumerOrgno") {
        required = true
        description = "The consumer's organisation number: 9 digits, for example `311718371`."
        schema = JsonSchema(type = JsonType.STRING, pattern = "^[0-9]{9}$")
    }
}

internal fun Responses.Builder.badRequest(description: String, vararg examples: Pair<String, ErrorResponse>) {
    HttpStatusCode.BadRequest {
        this.description = "$description Digdir can also reject the request with `400`, as `tag:kartverket.no,2026:upstream-rejected`."
        headers { requestId() }
        ContentType.Application.ProblemJson {
            schema = jsonSchema<ErrorResponse>()
            examples.forEach { (name, value) -> example(name, value) }
        }
    }
}

internal fun Responses.Builder.digdirUnreachableAndOtherStatuses(filtered: Boolean = false) {
    HttpStatusCode.BadGateway {
        description = if (filtered) {
            "A service this API depends on failed: Digdir, Maskinporten, the service that says which scopes the " +
                "calling app has access to, or Cloud KMS, which signs this API's requests for Maskinporten tokens. " +
                "Your request did not cause it."
        } else {
            "A service this API depends on failed: Digdir, Maskinporten, or Cloud KMS, which signs this API's " +
                "requests for Maskinporten tokens. Your request did not cause it."
        }
        headers { requestId() }
        ContentType.Application.ProblemJson {
            schema = jsonSchema<ErrorResponse>()
            example("UpstreamError", ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Digdir failed"))
            if (filtered) {
                example(
                    "FilteringError",
                    ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to the filtering service failed"),
                )
            }
            example("KmsError", ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Cloud KMS failed"))
        }
    }

    default {
        description = "Digdir rejected the request. The status is Digdir's, for example `403`, `404` or `409`, `type` " +
            "is `tag:kartverket.no,2026:upstream-rejected`, and `detail` is Digdir's explanation. A `500` with `type` " +
            "`tag:kartverket.no,2026:internal-error` is an unexpected error in this API."
        headers { requestId() }
        ContentType.Application.ProblemJson {
            schema = jsonSchema<ErrorResponse>()
            example(
                "UpstreamRejected",
                ErrorResponse.of(ProblemType.UPSTREAM_REJECTED, HttpStatusCode.Conflict, "The explanation Digdir gave"),
            )
            example("InternalError", ErrorResponse.of(ProblemType.INTERNAL_ERROR, HttpStatusCode.InternalServerError))
        }
    }
}

internal const val DIGDIR_ERRORS =
    "When Digdir rejects the request, the answer has Digdir's status and explanation, except a `401`, which this " +
        "API answers with `502`."

internal fun validationError(field: String, code: ValidationCode, message: String) = ErrorResponse.of(
    ProblemType.VALIDATION_ERROR,
    HttpStatusCode.BadRequest,
    errors = listOf(FieldError(field, code, message)),
)

internal val missingScopeExample = "MissingScope" to validationError("scope", ValidationCode.MISSING, "scope is required")

internal val invalidOrganisationNumberExample = "InvalidOrganisationNumber" to validationError(
    "consumerOrgno",
    ValidationCode.INVALID_FORMAT,
    "consumerOrgno must be an organisation number of 9 digits",
)

internal val missingClientCertificateExample = "MissingClientCertificate" to validationError(
    "X-Forwarded-Client-Cert",
    ValidationCode.MISSING,
    "X-Forwarded-Client-Cert is required",
)

internal const val INVALID_SCOPE_REASON =
    "is not exactly one `scope`, or `scope` is blank, contains whitespace or is not URL-encoded correctly."

internal const val INVALID_SCOPE = "The query $INVALID_SCOPE_REASON"

internal const val INVALID_ORGNO_OR_SCOPE = "`consumerOrgno` is not 9 digits, or the query $INVALID_SCOPE_REASON"

internal inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
