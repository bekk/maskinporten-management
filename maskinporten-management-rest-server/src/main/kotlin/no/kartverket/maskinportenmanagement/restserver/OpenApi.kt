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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.EmptySerializersModule
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccess
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessState

private val openApiJson = Json { prettyPrint = true }

private val schemaInference = KotlinxSerializerJsonSchemaInference(EmptySerializersModule())

private val exampleJson = Json { encodeDefaults = true }

private val apiInfo = OpenApiInfo(
    title = "Maskinporten Management REST API",
    version = "0.1.0",
    description = "REST API for managing Maskinporten scopes",
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

private val scopeAccessSchema = schemaInference.jsonSchema<ScopeAccess>().documented(
    "scope" to { copy(description = "The scope.") },
    "owner_orgno" to { copy(description = "The organisation that owns the scope: Kartverket.") },
    "consumer_orgno" to { copy(description = "The organisation that was given access.") },
    "state" to {
        copy(
            description = """
                Where the access stands. Only `APPROVED` means the organisation has access.

                - `REQUESTED`: the organisation has asked for access.
                - `APPROVED`: the organisation has access.
                - `DENIED`: the request for access was refused.
            """.trimIndent(),
        )
    },
    "created" to { copy(format = "date-time") },
    "last_updated" to { copy(format = "date-time") },
    required = null,
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
    required = listOf("error", "code"),
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
    scope = "kartverk:matrikkel.read",
    ownerOrgno = "971040238",
    ownerOrganizationName = "STATENS KARTVERK",
    consumerOrgno = "311718371",
    consumerOrganizationName = "EKSEMPEL AS",
    state = ScopeAccessState.APPROVED,
    created = "2026-01-15T09:30:00Z",
    lastUpdated = "2026-01-15T09:30:00Z",
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

private const val INVALID_ORGNO_OR_SCOPE = "`consumerOrgno` is not 9 digits, or the query is not exactly one " +
    "`scope`, or `scope` is blank or not URL-encoded correctly."

internal val scopesListOperation: Operation.Builder.() -> Unit = {
    summary = "List Kartverket's scopes that the calling app has access to"
    description = """
        Asks Digdir for Kartverket's active scopes, and returns the ones the calling app has access to: the scopes in
        its `exact` list, and the ones that start with one of its prefixes. Istio tells this API which app is calling,
        in the `X-Forwarded-Client-Cert` header.

        Each scope is Digdir's, unchanged. When Digdir answers with an error, that answer is returned unchanged.
    """.trimIndent()

    responses {
        HttpStatusCode.OK {
            description = "The scopes the calling app has access to. The list is empty if it has access to none."
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY)
            }
        }

        badRequest(
            "The `X-Forwarded-Client-Cert` header is missing, so this API cannot tell which app is calling.",
            missingClientCertificateExample,
        )
        digdirUnreachableAndOtherStatuses(filtered = true)
    }
}

internal val scopeAccessOrgsOperation: Operation.Builder.() -> Unit = {
    summary = "List the organisations that have access to a scope"
    description = "Asks Digdir which organisations Kartverket has given access to `scope`. Digdir's response is " +
        "returned unchanged, with its status, body and content type, including when Digdir answers with an error."

    parameters {
        scope()
    }

    responses {
        HttpStatusCode.OK {
            description = "Digdir's answer: the organisations given access to the scope. Check `state`: only " +
                "`APPROVED` means access, and an organisation that has only asked for access, or was refused, may " +
                "be listed too."
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(scopeAccessSchema))
                example("Consumers", listOf(exampleScopeAccess))
            }
        }

        badRequest(
            "The query is not exactly one `scope`, or `scope` is blank or not URL-encoded correctly.",
            missingScopeExample,
        )
        digdirUnreachableAndOtherStatuses()
    }
}

internal val scopeAccessScopesOperation: Operation.Builder.() -> Unit = {
    summary = "List the scopes an organisation has access to"
    description = """
        Asks Digdir which of Kartverket's scopes `orgnr` has access to, and returns the ones the calling app has
        access to: the scopes in its `exact` list, and the ones that start with one of its prefixes. Istio tells this
        API which app is calling, in the `X-Forwarded-Client-Cert` header.

        Each access is Digdir's, unchanged. When Digdir answers with an error, that answer is returned unchanged.
    """.trimIndent()

    parameters {
        orgnr()
    }

    responses {
        HttpStatusCode.OK {
            description = "The organisation's access to the scopes the calling app has access to. Check `state`: " +
                "only `APPROVED` means access, and a scope the organisation has only asked for, or was refused, may " +
                "be listed too. The list is empty if there is none."
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
    description = "Asks Digdir to give `consumerOrgno` access to `scope`. Digdir's response is returned unchanged, " +
        "with its status, body and content type, including when Digdir answers with an error."

    parameters {
        consumerOrgno()
        scope()
    }

    responses {
        HttpStatusCode.OK {
            description = "Digdir's answer: the access that was given."
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
    description = "Asks Digdir to remove the access `consumerOrgno` has to `scope`. Digdir's response is returned " +
        "unchanged, with its status, body and content type, including when Digdir answers with an error."

    parameters {
        consumerOrgno()
        scope()
    }

    responses {
        HttpStatusCode.OK {
            description = "Digdir's answer: the access that was removed."
            ContentType.Application.Json {
                schema = scopeAccessSchema
            }
        }

        badRequest(INVALID_ORGNO_OR_SCOPE, invalidOrganisationNumberExample, missingScopeExample)
        digdirUnreachableAndOtherStatuses()
    }
}

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
