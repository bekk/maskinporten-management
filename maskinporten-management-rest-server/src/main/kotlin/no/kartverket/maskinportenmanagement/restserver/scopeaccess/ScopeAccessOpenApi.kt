package no.kartverket.maskinportenmanagement.restserver.scopeaccess

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.jsonSchema
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import no.kartverket.maskinportenmanagement.restserver.DIGDIR_ERRORS
import no.kartverket.maskinportenmanagement.restserver.INVALID_ORGNO_OR_SCOPE
import no.kartverket.maskinportenmanagement.restserver.INVALID_SCOPE
import no.kartverket.maskinportenmanagement.restserver.badRequest
import no.kartverket.maskinportenmanagement.restserver.clientCertificate
import no.kartverket.maskinportenmanagement.restserver.consumerOrgno
import no.kartverket.maskinportenmanagement.restserver.digdirUnreachableAndOtherStatuses
import no.kartverket.maskinportenmanagement.restserver.example
import no.kartverket.maskinportenmanagement.restserver.invalidOrganisationNumberExample
import no.kartverket.maskinportenmanagement.restserver.missingClientCertificateExample
import no.kartverket.maskinportenmanagement.restserver.missingScopeExample
import no.kartverket.maskinportenmanagement.restserver.orgnr
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.requestId
import no.kartverket.maskinportenmanagement.restserver.scope
import no.kartverket.maskinportenmanagement.restserver.validationError
import kotlin.time.Instant

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
