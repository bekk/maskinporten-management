package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
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
import kotlinx.serialization.modules.EmptySerializersModule
import no.kartverket.maskinportenmanagement.client.IntegrationType
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.ScopeAccess
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import no.kartverket.maskinportenmanagement.client.ScopeDetails
import no.kartverket.maskinportenmanagement.client.TokenType
import no.kartverket.maskinportenmanagement.client.Visibility
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import kotlin.time.Instant

private val openApiJson = Json { prettyPrint = true }

private val schemaInference = KotlinxSerializerJsonSchemaInference(EmptySerializersModule())

private val exampleJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

private val apiInfo = OpenApiInfo(
    title = "Maskinporten Management REST API",
    version = "v0",
    description = """
        Manages Kartverket's Maskinporten scopes through Digdir's API: lists, creates, updates and deletes the scopes,
        and lists, gives and removes organisations' access to them.

        A call only reaches this API after the platform has checked that the calling app may make it. When a list
        holds scopes, it only shows the ones the calling app has access to. Answers use Digdir's field names, but only
        hold the fields described here: a field Digdir adds later is left out. When Digdir answers with an error, this
        API returns that answer unchanged: its status, body and content type.
    """.trimIndent(),
)

fun Application.openApiSpec(): String =
    openApiJson.encodeToString(OpenApiDoc(info = apiInfo) + plugin(RoutingRoot).getAllRoutes())

object OpenApiSpecFile {

    const val NAME: String = "openapi.json"

    fun contentsFor(servedSpec: String): String = servedSpec + "\n"
}

private fun JsonSchema.documented(
    vararg fields: Pair<String, JsonSchema.() -> JsonSchema>,
    required: List<String>? = this.required,
): JsonSchema {
    val documentation = fields.toMap()
    val unknown = (documentation.keys + required.orEmpty()) - properties?.keys.orEmpty()
    require(unknown.isEmpty()) {
        "$title has no ${unknown.joinToString()} - a renamed field leaves the spec describing one that is gone"
    }
    return copy(
        required = required,
        properties = properties?.mapValues { (field, schema) ->
            documentation[field]?.let { schema.mapValue(it) } ?: schema
        },
    )
}

// Like Digdir, this API leaves out a missing value instead of sending null
private fun JsonSchema.leftOutWhenMissing(description: String): JsonSchema =
    copy(type = JsonType.STRING, description = description)

private val scopeAccessSchema = schemaInference.jsonSchema<ScopeAccess>().documented(
    "scope" to { copy(description = "The scope.") },
    "owner_orgno" to { copy(description = "The organisation that owns the scope: Kartverket.") },
    "owner_organization_name" to {
        leftOutWhenMissing("The name of the organisation that owns the scope. Left out when Digdir has no name for it.")
    },
    "consumer_orgno" to { copy(description = "The organisation that was given access.") },
    "consumer_organization_name" to {
        leftOutWhenMissing("The name of the organisation that was given access. Left out when Digdir has no name for it.")
    },
    "state" to {
        copy(
            description = "Where the access stands. Only `APPROVED` means the organisation has access, and giving " +
                "access through this API sets it at once. Digdir does not document when it uses `REQUESTED` and " +
                "`DENIED`. `UNKNOWN` is a state Digdir has added that this API does not know yet.",
        )
    },
    "created" to { copy(description = "When the access was given.", format = "date-time") },
    "last_updated" to { copy(description = "When the access was last changed.", format = "date-time") },
)

private const val PERSONS_ONLY = "Only for logins with ID-porten, not for Maskinporten:"

private const val INTEGRATION_TYPES = "The kinds of client that can use the scope. A Maskinporten client needs " +
    "`maskinporten`, or an empty list, which allows every kind."

private val TOKEN_TYPES = """
    The kind of access token issued for the scope.

    - `SELF_CONTAINED`: a JWT that the API can verify itself. Maskinporten only issues these.
    - `OPAQUE`: a token that the API has to look up with Digdir.
""".trimIndent()

private val VISIBILITIES = """
    Who can see the scope.

    - `PUBLIC`: anyone, in Digdir's list of all scopes.
    - `PRIVATE`: only the owner and the organisations given access.
    - `INTERNAL`: Digdir's own use.
""".trimIndent()

private val scopeSchema = schemaInference.jsonSchema<ScopeDetails>().documented(
    "name" to { copy(description = "The whole scope: the prefix, a colon and the subscope.") },
    "prefix" to {
        copy(description = "Digdir's prefix for the owner, `kartverk` for Kartverket: only the part before the colon.")
    },
    "subscope" to { copy(description = "The part after the colon, which the owner chose. It cannot be changed.") },
    "description" to { copy(description = "A short description in Norwegian Bokmål.") },
    "long_description" to {
        leftOutWhenMissing("A longer description in Norwegian Bokmål. Left out, or empty, if the owner has not written one.")
    },
    "description#en" to { leftOutWhenMissing("`description` in English. Left out if the owner has not written one.") },
    "description#nn" to {
        leftOutWhenMissing("`description` in Norwegian Nynorsk. Left out if the owner has not written one.")
    },
    "long_description#en" to {
        leftOutWhenMissing("`long_description` in English. Left out if the owner has not written one.")
    },
    "long_description#nn" to {
        leftOutWhenMissing("`long_description` in Norwegian Nynorsk. Left out if the owner has not written one.")
    },
    "active" to {
        copy(description = "Always `true` in `GET /api/scopes`, which lists only active scopes, and `false` once the scope is deleted.")
    },
    "created" to { copy(description = "When the scope was created.", format = "date-time") },
    "last_updated" to { copy(description = "When the scope was last changed.", format = "date-time") },
    "allowed_integration_types" to {
        copy(description = "$INTEGRATION_TYPES `unknown` is a kind Digdir has added that this API does not know yet.")
    },
    "owner_orgno" to { copy(description = "The organisation that owns the scope: Kartverket.") },
    "token_type" to {
        copy(description = "$TOKEN_TYPES\n- `UNKNOWN`: a kind Digdir has added that this API does not know yet.")
    },
    "visibility" to {
        copy(description = "$VISIBILITIES\n- `UNKNOWN`: a value Digdir has added that this API does not know yet.")
    },
    "requires_user_consent" to {
        copy(description = "$PERSONS_ONLY a person must consent before a client gets a token.")
    },
    "requires_user_authentication" to {
        copy(description = "$PERSONS_ONLY a person must log in again, even with an active session.")
    },
    "requires_pseudonymous_tokens" to {
        copy(description = "$PERSONS_ONLY tokens leave out the person's national identity number.")
    },
    "delegation_source" to {
        leftOutWhenMissing(
            "Where an organisation with access can delegate it to a supplier, such as `https://www.altinn.no/`. Left " +
                "out when the scope cannot be delegated.",
        )
    },
    "at_max_age" to {
        copy(description = "The most seconds an access token for the scope can live. `0` means Digdir's default.")
    },
    "authorization_max_lifetime" to {
        copy(description = "The most seconds an authorization for the scope can live. `0` means Digdir's default.")
    },
    "accessible_for_all" to {
        copy(description = "If `true`, every organisation can get tokens for the scope without being given access.")
    },
    "enforced_aud_for_access_token" to { leftOutWhenMissing("Digdir does not document this field.") },
    "protected" to { copy(description = "Digdir does not document this field.") },
    "supports_european_businesses" to {
        copy(
            description = "Digdir does not document this field. Its name matches Digdir's feature that lets European " +
                "businesses get tokens with an eIDAS seal, without being given access.",
        )
    },
)

private val errorResponseSchema = schemaInference.jsonSchema<ErrorResponse>().documented(
    "error" to {
        copy(description = "A short summary for people to read. The wording may change, so check `code` in your code.")
    },
    "code" to {
        copy(
            description = """
                What went wrong. These values do not change, so your code can rely on them.

                - `INVALID_REQUEST` (400): the request was refused before it reached Digdir. `error` says why.
                - `UPSTREAM_ERROR` (502): this API could not get an answer from Digdir, or from the service that says
                  which scopes the calling app has access to. Your request did not cause it.
                - `INTERNAL_ERROR` (500): an unexpected error in this API.
            """.trimIndent(),
        )
    },
)

internal val healthLiveOperation: Operation.Builder.() -> Unit = {
    summary = "Liveness probe"
    description = "Returns 200 OK if the server is up. Not part of the stable API."
    responses {
        HttpStatusCode.OK {
            description = "OK"
        }
    }
}

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

private fun Parameters.Builder.scope() {
    query("scope") {
        required = true
        description = "The scope, for example `kartverk:matrikkel.read`. It must be the only query parameter, sent " +
            "once."
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
        required = true
        description = "Says which app is calling. Istio usually sets it on every call, so the app does not."
        schema = JsonSchema(type = JsonType.STRING)
    }
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
        this.description = "$description Digdir can also answer `400`, in its own error format."
        ContentType.Application.Json {
            schema = errorResponseSchema
            examples.forEach { (name, value) -> example(name, value) }
        }
    }
}

private fun Responses.Builder.digdirUnreachableAndOtherStatuses(filtered: Boolean = false) {
    HttpStatusCode.BadGateway {
        description = if (filtered) {
            "This API could not get an answer from Digdir, or from the service that says which scopes the calling " +
                "app has access to. Your request did not cause it."
        } else {
            "This API could not get an answer from Digdir. Your request did not cause it."
        }
        ContentType.Application.Json {
            schema = errorResponseSchema
            example("UpstreamError", ErrorResponse("The call to Digdir failed", ErrorCode.UPSTREAM_ERROR))
            if (filtered) {
                example(
                    "FilteringError",
                    ErrorResponse("The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR),
                )
            }
        }
    }

    default {
        description = "Any other status is Digdir's own answer, passed on unchanged in Digdir's error format. " +
            "The exception is a `500` with `code` `INTERNAL_ERROR`, which is an unexpected error in this API."
    }
}

private const val DIGDIR_ERRORS = "When Digdir answers with an error, that answer is returned unchanged."

private val missingScopeExample =
    "MissingScope" to ErrorResponse("Query parameter scope is required", ErrorCode.INVALID_REQUEST)

private val invalidOrganisationNumberExample = "InvalidOrganisationNumber" to ErrorResponse(
    "Path parameter consumerOrgno must be an organisation number of 9 digits",
    ErrorCode.INVALID_REQUEST,
)

private val missingClientCertificateExample = "MissingClientCertificate" to ErrorResponse(
    "Header X-Forwarded-Client-Cert is required",
    ErrorCode.INVALID_REQUEST,
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

// A new scope cannot hold a value this API does not know, so UNKNOWN is left out of it
private fun JsonSchema.withoutUnknown(): JsonSchema = copy(
    enum = enum?.filterNot { it?.deserialize(String.serializer()).equals("unknown", ignoreCase = true) },
    items = items?.mapValue { it.withoutUnknown() },
)

private val newScopeSchema = scopeSchema.copy(
    title = "NewScope",
    properties = scopeSchema.properties?.filterKeys { it !in setOf("name", "created", "last_updated") },
    required = listOf("prefix", "subscope"),
).documented(
    "active" to { copy(description = "Whether clients can get tokens for the scope.") },
    "owner_orgno" to { copy(description = "Leave it out: Digdir makes the calling organisation, Kartverket, the owner.") },
    "allowed_integration_types" to { withoutUnknown().copy(description = INTEGRATION_TYPES) },
    "token_type" to { withoutUnknown().copy(description = TOKEN_TYPES) },
    "visibility" to { withoutUnknown().copy(description = VISIBILITIES) },
)

private val exampleNewScope = buildJsonObject {
    put("prefix", "kartverk")
    put("subscope", "tilgangsstyring/demo.read")
    put("description", "Lesetilgang til demo-API-et")
    put("description#en", "Read access to the demo API")
    putJsonArray("allowed_integration_types") { add("maskinporten") }
    put("visibility", "PRIVATE")
}

internal val scopeCreateOperation: Operation.Builder.() -> Unit = {
    summary = "Create a scope"
    description = """
        Asks Digdir to create the scope in the body, which Kartverket then owns. The body is in Digdir's format and is
        sent on unchanged. It may only hold the fields listed here, each once and spelled exactly so, so that the scope
        cannot be read in two ways.

        $DIGDIR_ERRORS
    """.trimIndent()

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
            ContentType.Application.Json {
                schema = scopeSchema
                example("CreatedScope", exampleScope)
            }
        }

        badRequest(
            "The URL has a query, or the body is not a JSON object in UTF-8 with `Content-Type: application/json`, or " +
                "it has a field that is not listed here, or the same field twice.",
            "UnknownField" to ErrorResponse("Digdir has no field \"Subscope\"", ErrorCode.INVALID_REQUEST),
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
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = scopeSchema
            example("ScopeUpdate", exampleUpdatedScope)
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The scope after the update."
            ContentType.Application.Json {
                schema = scopeSchema
                example("UpdatedScope", exampleUpdatedScope)
            }
        }

        badRequest("$INVALID_SCOPE Or the body is not sent with `Content-Type: application/json`.", missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

private val exampleUpdatedScope = exampleScope.copy(description = "Lesetilgang til demo-API-et, også for leverandører")

internal val scopesListOperation: Operation.Builder.() -> Unit = {
    summary = "List Kartverket's scopes that the calling app has access to"
    description = """
        Asks Digdir for Kartverket's active scopes, and returns the ones the calling app has access to: the scopes in
        its `exact` list, and the ones that start with one of its prefixes. This API reads which app is calling from
        the `X-Forwarded-Client-Cert` header, which Istio usually sets.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        clientCertificate()
    }

    responses {
        HttpStatusCode.OK {
            description = "The scopes the calling app has access to. The list is empty if it has access to none."
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(scopeSchema))
                example("Scopes", listOf(exampleScope))
            }
        }

        badRequest(
            "The `X-Forwarded-Client-Cert` header is missing, so this API cannot tell which app is calling.",
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
    }

    responses {
        HttpStatusCode.OK {
            description = "The scope that was deleted."
            ContentType.Application.Json {
                schema = scopeSchema
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
    }

    responses {
        HttpStatusCode.OK {
            description = "The organisations given access to the scope. Check `state`: only `APPROVED` means access."
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(scopeAccessSchema))
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
        Asks Digdir which of Kartverket's scopes `orgnr` has access to, and returns the ones the calling app has
        access to: the scopes in its `exact` list, and the ones that start with one of its prefixes. This API reads
        which app is calling from the `X-Forwarded-Client-Cert` header, which Istio usually sets.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        orgnr()
        clientCertificate()
    }

    responses {
        HttpStatusCode.OK {
            description = "The organisation's access to the scopes the calling app has access to. Check `state`: " +
                "only `APPROVED` means access. The list is empty if there is none."
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(scopeAccessSchema))
                example("Scopes", listOf(exampleScopeAccess))
            }
        }

        badRequest(
            "The query is not exactly one `orgnr`, or `orgnr` is not 9 digits, or the `X-Forwarded-Client-Cert` " +
                "header is missing.",
            "InvalidOrganisationNumber" to ErrorResponse(
                "Query parameter orgnr must be an organisation number of 9 digits",
                ErrorCode.INVALID_REQUEST,
            ),
            missingClientCertificateExample,
        )
        digdirUnreachableAndOtherStatuses(filtered = true)
    }
}

internal val scopeAccessGrantOperation: Operation.Builder.() -> Unit = {
    summary = "Give an organisation access to a scope"
    description = "Asks Digdir to give `consumerOrgno` access to `scope`. $DIGDIR_ERRORS"

    parameters {
        consumerOrgno()
        scope()
    }

    responses {
        HttpStatusCode.OK {
            description = "The access that was given."
            ContentType.Application.Json {
                schema = scopeAccessSchema
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
    }

    responses {
        HttpStatusCode.OK {
            description = "The access that was removed."
            ContentType.Application.Json {
                schema = scopeAccessSchema
                example("RemovedAccess", exampleScopeAccess)
            }
        }

        badRequest(INVALID_ORGNO_OR_SCOPE, invalidOrganisationNumberExample, missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
