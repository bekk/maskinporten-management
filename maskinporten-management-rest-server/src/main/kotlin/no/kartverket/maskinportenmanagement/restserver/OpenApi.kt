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
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.EmptySerializersModule
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import no.kartverket.maskinportenmanagement.client.ScopeName
import no.kartverket.maskinportenmanagement.client.validation.ScopeAccessValidation
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.models.ConsumerAccess
import no.kartverket.maskinportenmanagement.restserver.models.ErrorCode
import no.kartverket.maskinportenmanagement.restserver.models.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.models.FieldError
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessRequest
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessResponse

private val openApiJson = Json { prettyPrint = true }

private val schemaInference = KotlinxSerializerJsonSchemaInference(EmptySerializersModule())

@OptIn(ExperimentalSerializationApi::class)
private val exampleJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

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

private val scopeAccessRequestSchema = schemaInference.jsonSchema<ScopeAccessRequest>().documented(
    "scope" to {
        copy(
            type = JsonType.STRING,
            pattern = ScopeAccessValidation.SCOPE_FORMAT.pattern,
            description = "The full scope name: a prefix and a subscope separated by a colon, " +
                "e.g. \"kartverk:matrikkel.read\".",
        )
    },
    required = listOf("scope"),
)

private val scopeAccessResponseSchema = schemaInference.jsonSchema<ScopeAccessResponse>().documented(
    "access" to {
        copy(
            description = "Every consumer organization that has, has requested or has been denied access to the " +
                "scope. Empty if there are none, including when the scope does not exist.",
        )
    },
)

private val errorResponseSchema = schemaInference.jsonSchema<ErrorResponse>().documented(
    "error" to {
        copy(description = "Human-readable summary. Not stable - branch on `code`, not on this.")
    },
    "code" to { copy(description = "Stable machine-readable code.") },
    "errors" to {
        copy(description = "Present when code is VALIDATION_ERROR. Every field that failed, not just the first.")
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

internal val scopeAccessOperation: Operation.Builder.() -> Unit = {
    summary = "List the consumers of a scope"
    description = "Lists the organizations that have access to [scope], with the state of each one's access. " +
        "The scope is sent in the body rather than the path, since scope names contain colons and slashes."

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = scopeAccessRequestSchema
            example("Example", ScopeAccessRequest(ScopeName.parse("kartverk:matrikkel.read")))
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The consumers of the scope."
            ContentType.Application.Json {
                schema = scopeAccessResponseSchema
                example(
                    "Example",
                    ScopeAccessResponse(
                        listOf(
                            ConsumerAccess(
                                scope = "kartverk:matrikkel.read",
                                ownerOrgno = "971040238",
                                ownerOrganizationName = "Statens kartverk",
                                consumerOrgno = "971032081",
                                consumerOrganizationName = "Statens vegvesen",
                                state = ScopeAccessState.APPROVED,
                                created = "2026-03-01T08:00:00Z",
                                lastUpdated = "2026-03-01T08:00:00Z",
                            ),
                        ),
                    ),
                )
                example("NoConsumers", ScopeAccessResponse(emptyList()))
            }
        }

        HttpStatusCode.BadRequest {
            description = "The request could not be understood. Validation failures carry code VALIDATION_ERROR " +
                "and list every failing field in \"errors\". A body that is not valid JSON, or has a field of the " +
                "wrong type, carries MALFORMED_BODY."
            ContentType.Application.Json {
                schema = errorResponseSchema
                example(
                    "MissingScope",
                    ErrorResponse(
                        error = "Validation failed",
                        code = ErrorCode.VALIDATION_ERROR,
                        errors = listOf(FieldError("scope", ValidationCode.MISSING, "scope is required")),
                    ),
                )
                example("MalformedBody", ErrorResponse("Malformed request body", ErrorCode.MALFORMED_BODY))
                example(
                    "MaskinportenRejected",
                    ErrorResponse("Maskinporten rejected the request", ErrorCode.UPSTREAM_REJECTED),
                )
            }
        }

        HttpStatusCode.BadGateway {
            description = "The call to Maskinporten failed for a reason unrelated to this request's content. The " +
                "response never includes Maskinporten's own error details; those are logged server-side instead."
            ContentType.Application.Json {
                schema = errorResponseSchema
                example("Example", ErrorResponse("The call to Maskinporten failed", ErrorCode.UPSTREAM_ERROR))
            }
        }

        HttpStatusCode.InternalServerError {
            description = "An unanticipated server error."
            ContentType.Application.Json {
                schema = errorResponseSchema
                example("Example", ErrorResponse("Internal server error", ErrorCode.INTERNAL_ERROR))
            }
        }
    }
}

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
