package no.kartverket.__pkg__.restserver

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
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.Responses
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
import no.kartverket.__pkg__.client.ExampleItem
import no.kartverket.__pkg__.client.validation.ValidationCode
import no.kartverket.__pkg__.restserver.models.ErrorCode
import no.kartverket.__pkg__.restserver.models.ErrorResponse
import no.kartverket.__pkg__.restserver.models.FieldError

private val openApiJson = Json { prettyPrint = true }

private val schemaInference = KotlinxSerializerJsonSchemaInference(EmptySerializersModule())

@OptIn(ExperimentalSerializationApi::class)
private val exampleJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

private val apiInfo = OpenApiInfo(
    title = "__Title__ REST API",
    version = "v0",
    // TODO: What the API does for its callers, and how to use it
    description = """
        Calls __Upstream__ on behalf of Kartverket's teams, so they do not have to deal with __Upstream__ themselves.
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

private val fieldErrorSchema = schemaInference.jsonSchema<FieldError>().documented(
    "field" to { copy(description = "The request field, for example `id`.") },
    "code" to {
        copy(
            description = """
                - `MISSING`: the field is absent, null or blank.
                - `INVALID_FORMAT`: the field is present but not valid.
            """.trimIndent(),
        )
    },
    "message" to { copy(description = "What is wrong, for people to read. The wording may change.") },
)

private val errorResponseSchema = schemaInference.jsonSchema<ErrorResponse>().documented(
    "error" to {
        copy(description = "A short summary for people to read. The wording may change, so check `code` in your code.")
    },
    "code" to {
        copy(
            description = """
                What went wrong. These values do not change, so your code can rely on them.

                - `MALFORMED_REQUEST` (400): the request could not be read, for example a body that is not JSON or a
                  URL that is not encoded correctly.
                - `VALIDATION_ERROR` (400): one or more fields are missing or invalid. `errors` lists all of them.
                - `UPSTREAM_REJECTED` (4xx): the request passed this API's checks, but __Upstream__ rejected it. The
                  status is __Upstream__'s.
                - `UPSTREAM_ERROR` (502): this API could not get an answer from __Upstream__. Your request did not
                  cause it.
                - `INTERNAL_ERROR` (500): an unexpected error in this API.
            """.trimIndent(),
        )
    },
    "errors" to {
        copy(
            type = JsonType.ARRAY,
            items = ReferenceOr.Value(fieldErrorSchema),
            description = "Only with `VALIDATION_ERROR`: every field that failed, so you can fix them all at once.",
        )
    },
    required = listOf("error", "code"),
)

// TODO: Replace with the real schemas and operations
private val exampleItemSchema = schemaInference.jsonSchema<ExampleItem>().documented(
    "id" to { copy(description = "The item's id at __Upstream__.") },
    "name" to { copy(description = "The item's name.") },
)

internal val exampleItemOperation: Operation.Builder.() -> Unit = {
    summary = "Get an example item"
    description = "Gets the item `id` from __Upstream__."

    parameters {
        path("id") {
            required = true
            description = "The item's id: 1 to 64 letters, digits, underscores or hyphens."
            schema = JsonSchema(type = JsonType.STRING, pattern = "^[A-Za-z0-9_-]{1,64}$")
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The item."
            ContentType.Application.Json {
                schema = exampleItemSchema
                example("Item", ExampleItem(id = "abc", name = "Example"))
            }
        }

        HttpStatusCode.BadRequest {
            description = "The request was not accepted. `code` says why."
            ContentType.Application.Json {
                schema = errorResponseSchema
                example(
                    "InvalidId",
                    ErrorResponse(
                        error = "Validation failed",
                        code = ErrorCode.VALIDATION_ERROR,
                        errors = listOf(
                            FieldError(
                                "id",
                                ValidationCode.INVALID_FORMAT,
                                "id must be 1 to 64 letters, digits, underscores or hyphens",
                            ),
                        ),
                    ),
                )
            }
        }

        HttpStatusCode.NotFound {
            description = "__Upstream__ has no item `id`."
            ContentType.Application.Json {
                schema = errorResponseSchema
                example("NotFound", ErrorResponse("__Upstream__ rejected the request", ErrorCode.UPSTREAM_REJECTED))
            }
        }

        upstreamAndInternalErrors()
    }
}

private fun Responses.Builder.upstreamAndInternalErrors() {
    HttpStatusCode.BadGateway {
        description = "This API could not get an answer from __Upstream__. Your request did not cause it."
        ContentType.Application.Json {
            schema = errorResponseSchema
            example("UpstreamError", ErrorResponse("The call to __Upstream__ failed", ErrorCode.UPSTREAM_ERROR))
        }
    }

    HttpStatusCode.InternalServerError {
        description = "An unexpected error in this API."
        ContentType.Application.Json {
            schema = errorResponseSchema
            example("InternalError", ErrorResponse("Internal server error", ErrorCode.INTERNAL_ERROR))
        }
    }
}

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
