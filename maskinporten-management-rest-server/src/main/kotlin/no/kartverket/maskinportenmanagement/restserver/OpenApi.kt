package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.Headers
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.KotlinxSerializerJsonSchemaInference
import io.ktor.openapi.MediaType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.Parameters
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.Responses
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import no.kartverket.maskinportenmanagement.client.IntegrationType
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import no.kartverket.maskinportenmanagement.client.TokenType
import no.kartverket.maskinportenmanagement.client.Visibility
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.INTEGRATION_TYPES
import no.kartverket.maskinportenmanagement.restserver.models.PageMetadata
import no.kartverket.maskinportenmanagement.restserver.models.ProblemType
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccess
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessChange
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessChangelog
import no.kartverket.maskinportenmanagement.restserver.models.ScopeDetails
import no.kartverket.maskinportenmanagement.restserver.models.TOKEN_TYPES
import no.kartverket.maskinportenmanagement.restserver.models.VISIBILITIES
import no.kartverket.maskinportenmanagement.restserver.models.ValidationCode
import kotlin.time.Instant

private val openApiJson = Json { prettyPrint = true }

private val exampleJson = Json {
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

private val exampleScopeAccess = ScopeAccess(
    scope = Scope.of("kartverk:matrikkel.read"),
    ownerOrgno = OrganizationNumber.of("971040238"),
    ownerOrganizationName = "STATENS KARTVERK",
    consumerOrgno = OrganizationNumber.of("311718371"),
    consumerOrganizationName = "EKSEMPEL AS",
    state = ScopeAccessState.APPROVED,
    created = Instant.parse("2026-01-15T09:30:00Z"),
    lastUpdated = Instant.parse("2026-01-15T09:30:00Z"),
)

private fun Parameters.Builder.scope(onlyParameter: Boolean = true) {
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

private fun Parameters.Builder.orgnr() {
    query("orgnr") {
        required = true
        description = "The consumer's organisation number: 9 digits, for example `311718371`. It must be the only " +
            "query parameter, sent once."
        schema = JsonSchema(type = JsonType.STRING, pattern = "^[0-9]{9}$")
    }
}

private fun Parameters.Builder.clientCertificate() {
    header(CLIENT_CERTIFICATE_HEADER) {
        description = "Says which app is calling. Istio usually sets it on every call, so the app does not. This " +
            "API requires it when filtering is on."
        schema = JsonSchema(type = JsonType.STRING)
    }
}

private fun Parameters.Builder.requestId() {
    header(HttpHeaders.XRequestId) {
        description = "An id to find this call by in this API's logs. Up to 128 ASCII letters, digits, `-`, `_`, " +
            "`.` and `:`. Without it, or with any other value, this API makes a new one. Every response has the id " +
            "in its `X-Request-Id` header."
        schema = JsonSchema(type = JsonType.STRING)
        example = GenericElement("3f2b8c1e-7d4a-4f0e-9b6a-2c5d8e1f4a7b")
    }
}

private fun Headers.Builder.requestId() = header(HttpHeaders.XRequestId) {
    description = "The id of this call in this API's logs: the `X-Request-Id` you sent, or a new one."
    required = true
    schema = JsonSchema(type = JsonType.STRING)
}

private fun Parameters.Builder.consumerOrgno() {
    path("consumerOrgno") {
        required = true
        description = "The consumer's organisation number: 9 digits, for example `311718371`."
        schema = JsonSchema(type = JsonType.STRING, pattern = "^[0-9]{9}$")
    }
}

private fun Responses.Builder.badRequest(description: String, vararg examples: Pair<String, ErrorResponse>) {
    HttpStatusCode.BadRequest {
        this.description = "$description Digdir can also reject the request with `400`, as `tag:kartverket.no,2026:upstream-rejected`."
        headers { requestId() }
        ContentType.Application.ProblemJson {
            schema = jsonSchema<ErrorResponse>()
            examples.forEach { (name, value) -> example(name, value) }
        }
    }
}

private fun Responses.Builder.digdirUnreachableAndOtherStatuses(filtered: Boolean = false) {
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

private const val DIGDIR_ERRORS =
    "When Digdir rejects the request, the answer has Digdir's status and explanation, except a `401`, which this " +
        "API answers with `502`."

private fun validationError(field: String, code: ValidationCode, message: String) = ErrorResponse.of(
    ProblemType.VALIDATION_ERROR,
    HttpStatusCode.BadRequest,
    errors = listOf(FieldError(field, code, message)),
)

private val missingScopeExample = "MissingScope" to validationError("scope", ValidationCode.MISSING, "scope is required")

private val invalidOrganisationNumberExample = "InvalidOrganisationNumber" to validationError(
    "consumerOrgno",
    ValidationCode.INVALID_FORMAT,
    "consumerOrgno must be an organisation number of 9 digits",
)

private val missingClientCertificateExample = "MissingClientCertificate" to validationError(
    "X-Forwarded-Client-Cert",
    ValidationCode.MISSING,
    "X-Forwarded-Client-Cert is required",
)

private const val INVALID_SCOPE_REASON =
    "is not exactly one `scope`, or `scope` is blank, contains whitespace or is not URL-encoded correctly."

private const val INVALID_SCOPE = "The query $INVALID_SCOPE_REASON"

private const val INVALID_ORGNO_OR_SCOPE = "`consumerOrgno` is not 9 digits, or the query $INVALID_SCOPE_REASON"

private val exampleScope = ScopeDetails(
    name = Scope.of("kartverk:tilgangsstyring/demo.read"),
    prefix = "kartverk",
    subscope = "tilgangsstyring/demo.read",
    description = "Lesetilgang til demo-API-et",
    active = true,
    created = Instant.parse("2026-01-15T09:30:00Z"),
    lastUpdated = Instant.parse("2026-01-15T09:30:00Z"),
    allowedIntegrationTypes = listOf(IntegrationType.MASKINPORTEN),
    ownerOrgno = OrganizationNumber.of("971040238"),
    tokenType = TokenType.SELF_CONTAINED,
    visibility = Visibility.PUBLIC,
    requiresUserConsent = false,
    requiresUserAuthentication = false,
    requiresPseudonymousTokens = false,
    atMaxAge = 120,
    authorizationMaxLifetime = 0,
    accessibleForAll = false,
    protected = false,
    supportsEuropeanBusinesses = false,
)

private fun JsonSchema.withoutUnknown(): JsonSchema = copy(
    enum = enum?.filterNot { it?.deserialize(String.serializer()).equals("unknown", ignoreCase = true) },
    items = items?.mapValue { it.withoutUnknown() },
)

private val newScopeDescriptions = mapOf(
    "active" to "Whether clients can get tokens for the scope.",
    "owner_orgno" to "Leave it out: Digdir makes the calling organisation, Kartverket, the owner.",
    "allowed_integration_types" to INTEGRATION_TYPES,
    "token_type" to TOKEN_TYPES,
    "visibility" to VISIBILITIES,
)

// The body is sent on to Digdir as it is, so it has no type of its own: it is a scope without the fields Digdir sets
private val newScopeSchema = KotlinxSerializerJsonSchemaInference.Default.jsonSchema<ScopeDetails>().run {
    copy(
        title = "NewScope",
        properties = properties
            ?.filterKeys { it !in setOf("name", "created", "last_updated") }
            ?.mapValues { (field, schema) ->
                schema.mapValue { it.withoutUnknown().copy(description = newScopeDescriptions[field] ?: it.description) }
            },
        required = listOf("prefix", "subscope"),
        additionalProperties = AdditionalProperties.Allowed(false),
    )
}

private val exampleNewScope = buildJsonObject {
    put("prefix", "kartverk")
    put("subscope", "tilgangsstyring/demo.read")
    put("description", "Lesetilgang til demo-API-et")
    put("description#en", "Read access to the demo API")
    putJsonArray("allowed_integration_types") { add("maskinporten") }
    put("visibility", "PRIVATE")
}

private val exampleCreatedScope =
    exampleScope.copy(descriptionEn = "Read access to the demo API", visibility = Visibility.PRIVATE)

internal val scopeCreateOperation: Operation.Builder.() -> Unit = {
    summary = "Create a scope"
    description = """
        Asks Digdir to create the scope in the body, which Kartverket then owns. The body is in Digdir's format and is
        sent on unchanged. It may only hold the fields listed here, each once and spelled exactly so, so that the scope
        cannot be read in two ways.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = newScopeSchema
            example("NewScope", exampleNewScope)
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The scope that was created."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<ScopeDetails>()
                example("CreatedScope", exampleCreatedScope)
            }
        }

        badRequest(
            "The URL has a query, or the body is not a JSON object in UTF-8 with `Content-Type: application/json`, or " +
                "it has a field that is not listed here, or the same field twice, or `prefix` or `subscope` is missing, " +
                "blank or not text.",
            "UnknownField" to validationError("Subscope", ValidationCode.NOT_ALLOWED, "Subscope is not allowed"),
        )
        digdirUnreachableAndOtherStatuses()
    }
}

internal val scopeUpdateOperation: Operation.Builder.() -> Unit = {
    summary = "Update a scope"
    description = """
        Asks Digdir to update `scope`. Get the scope first, change the fields you want to change, and send the whole
        scope back in the body. The body is sent on to Digdir unchanged, and Digdir decides what it accepts. The
        scope's name cannot be changed: `scope` in the query decides which scope is updated.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        scope()
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<ScopeDetails>()
            example("ScopeUpdate", exampleUpdatedScope)
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The scope after the update."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<ScopeDetails>()
                example("UpdatedScope", exampleUpdatedScope)
            }
        }

        badRequest("$INVALID_SCOPE Or the body is not in UTF-8 with `Content-Type: application/json`.", missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

private val exampleUpdatedScope = exampleScope.copy(description = "Lesetilgang til demo-API-et, også for leverandører")

internal val scopesListOperation: Operation.Builder.() -> Unit = {
    summary = "List Kartverket's scopes"
    description = """
        Asks Digdir for Kartverket's active scopes. When filtering is on, this API only returns the ones the calling
        app has access to: the scopes in its `exact` list, and the ones that start with one of its prefixes. This API
        reads which app is calling from the `X-Forwarded-Client-Cert` header, which Istio usually sets.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        clientCertificate()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "Kartverket's active scopes, or when filtering is on, the ones the calling app has " +
                "access to. The list is empty if there are none."
            headers { requestId() }
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(jsonSchema<ScopeDetails>()))
                example("Scopes", listOf(exampleScope))
            }
        }

        badRequest(
            "Filtering is on and the `X-Forwarded-Client-Cert` header is missing, so this API cannot tell which " +
                "app is calling.",
            missingClientCertificateExample,
        )
        digdirUnreachableAndOtherStatuses(filtered = true)
    }
}

internal val scopeDeleteOperation: Operation.Builder.() -> Unit = {
    summary = "Delete a scope"
    description = "Asks Digdir to delete `scope`. Digdir keeps the scope with `active` set to `false`, so it no longer " +
        "shows up in `GET /api/scopes`. $DIGDIR_ERRORS"

    parameters {
        scope()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The scope that was deleted."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<ScopeDetails>()
                example("DeletedScope", exampleScope.copy(active = false))
            }
        }

        badRequest(INVALID_SCOPE, missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

internal val scopeAccessOrgsOperation: Operation.Builder.() -> Unit = {
    summary = "List the organisations that have access to a scope"
    description = "Asks Digdir which organisations Kartverket has given access to `scope`. $DIGDIR_ERRORS"

    parameters {
        scope()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The organisations given access to the scope. Check `state`: only `APPROVED` means access."
            headers { requestId() }
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(jsonSchema<ScopeAccess>()))
                example("Consumers", listOf(exampleScopeAccess))
            }
        }

        badRequest(INVALID_SCOPE, missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

internal val scopeAccessScopesOperation: Operation.Builder.() -> Unit = {
    summary = "List the scopes an organisation has access to"
    description = """
        Asks Digdir which of Kartverket's scopes `orgnr` has access to. When filtering is on, this API only returns
        the ones the calling app has access to: the scopes in its `exact` list, and the ones that start with one of its
        prefixes. This API reads which app is calling from the `X-Forwarded-Client-Cert` header, which Istio usually
        sets.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        orgnr()
        clientCertificate()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The organisation's access to Kartverket's scopes, or when filtering is on, to the " +
                "scopes the calling app has access to. Check `state`: only `APPROVED` means access. The list is empty " +
                "if there is none."
            headers { requestId() }
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(jsonSchema<ScopeAccess>()))
                example("Scopes", listOf(exampleScopeAccess))
            }
        }

        badRequest(
            "The query is not exactly one `orgnr`, or `orgnr` is not 9 digits, or filtering is on and the " +
                "`X-Forwarded-Client-Cert` header is missing.",
            "InvalidOrganisationNumber" to validationError(
                "orgnr",
                ValidationCode.INVALID_FORMAT,
                "orgnr must be an organisation number of 9 digits",
            ),
            missingClientCertificateExample,
        )
        digdirUnreachableAndOtherStatuses(filtered = true)
    }
}

private val exampleScopeAccessChangelog = ScopeAccessChangelog(
    content = listOf(
        ScopeAccessChange(
            id = 1,
            created = Instant.parse("2026-01-15T09:30:00Z"),
            message = "Scope access created",
            actorName = "Kartverket",
            channel = "API",
            diff = """{"consumer_orgno":"311718371","state":"APPROVED"}""",
        ),
    ),
    page = PageMetadata(size = 20, number = 0, totalElements = 1, totalPages = 1),
)

internal val scopeAccessChangelogOperation: Operation.Builder.() -> Unit = {
    summary = "List the changes to who has access to a scope"
    description = """
        Asks Digdir for the changes to the access to `scope`, such as an organisation being given access or losing it.
        Digdir answers a page at a time. Without `page` and `size`, it gives its first page, in a size it picks.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        scope(onlyParameter = false)
        query("page") {
            description = "Which page to get, counting from 0."
            schema = JsonSchema(type = JsonType.INTEGER, minimum = 0.0)
        }
        query("size") {
            description = "The most changes a page holds."
            schema = JsonSchema(type = JsonType.INTEGER, minimum = 1.0)
        }
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "One page of the changes."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<ScopeAccessChangelog>()
                example("Changelog", exampleScopeAccessChangelog)
            }
        }

        badRequest(
            "The query has a parameter other than `scope`, `page` and `size`, or one of them twice or not " +
                "URL-encoded correctly. Or `scope` is missing, blank or contains whitespace, or `page` is not a whole " +
                "number of 0 or more, or `size` is not a whole number of 1 or more.",
            missingScopeExample,
            "InvalidPage" to validationError("page", ValidationCode.INVALID_FORMAT, "page must be a whole number of 0 or more"),
        )
        digdirUnreachableAndOtherStatuses()
    }
}

internal val scopeAccessGrantOperation: Operation.Builder.() -> Unit = {
    summary = "Give an organisation access to a scope"
    description = "Asks Digdir to give `consumerOrgno` access to `scope`. $DIGDIR_ERRORS"

    parameters {
        consumerOrgno()
        scope()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The access that was given."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<ScopeAccess>()
                example("GivenAccess", exampleScopeAccess)
            }
        }

        badRequest(INVALID_ORGNO_OR_SCOPE, invalidOrganisationNumberExample, missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

internal val scopeAccessRemoveOperation: Operation.Builder.() -> Unit = {
    summary = "Remove an organisation's access to a scope"
    description = "Asks Digdir to remove the access `consumerOrgno` has to `scope`. Digdir keeps the access as " +
        "inactive, so it no longer shows up in this API's lists. $DIGDIR_ERRORS"

    parameters {
        consumerOrgno()
        scope()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The access that was removed."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<ScopeAccess>()
                example("RemovedAccess", exampleScopeAccess)
            }
        }

        badRequest(INVALID_ORGNO_OR_SCOPE, invalidOrganisationNumberExample, missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
