package no.kartverket.maskinportenmanagement.restserver.scopes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.KotlinxSerializerJsonSchemaInference
import io.ktor.openapi.Operation
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.jsonSchema
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import no.kartverket.maskinportenmanagement.client.IntegrationType
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.TokenType
import no.kartverket.maskinportenmanagement.client.Visibility
import no.kartverket.maskinportenmanagement.restserver.DIGDIR_ERRORS
import no.kartverket.maskinportenmanagement.restserver.INVALID_SCOPE
import no.kartverket.maskinportenmanagement.restserver.badRequest
import no.kartverket.maskinportenmanagement.restserver.clientCertificate
import no.kartverket.maskinportenmanagement.restserver.digdirUnreachableAndOtherStatuses
import no.kartverket.maskinportenmanagement.restserver.example
import no.kartverket.maskinportenmanagement.restserver.missingClientCertificateExample
import no.kartverket.maskinportenmanagement.restserver.missingScopeExample
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.requestId
import no.kartverket.maskinportenmanagement.restserver.scope
import no.kartverket.maskinportenmanagement.restserver.validationError
import kotlin.time.Instant

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

// A body is sent on to Digdir as it is, so it has no type of its own: it is a scope without the fields Digdir sets
private fun scopeBodySchema(title: String, descriptions: Map<String, String> = emptyMap()) =
    KotlinxSerializerJsonSchemaInference.Default.jsonSchema<ScopeDetails>().run {
        copy(
            title = title,
            properties = properties
                ?.filterKeys { it !in setOf("name", "created", "last_updated") }
                ?.mapValues { (field, schema) ->
                    schema.mapValue { it.withoutUnknown().copy(description = descriptions[field] ?: it.description) }
                },
            required = null,
        )
    }

private val newScopeSchema = scopeBodySchema("NewScope", newScopeDescriptions)
    .copy(required = listOf("prefix", "subscope"), additionalProperties = AdditionalProperties.Allowed(false))

private val scopeUpdateSchema = scopeBodySchema("ScopeUpdate")

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
        scope's name cannot be changed: `scope` in the query decides which scope is updated. A value shown as
        `UNKNOWN` is one this API does not know yet, and Digdir does not accept it back.

        $DIGDIR_ERRORS
    """.trimIndent()

    parameters {
        scope()
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = scopeUpdateSchema
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
