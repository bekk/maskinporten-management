package com.maskinporten.maskinporten

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey

class UnauthorizedException(message: String) : RuntimeException(message)

class ForbiddenException(message: String) : RuntimeException(message)

private const val READ = "idporten:scopes.read"
private const val WRITE = "idporten:scopes.write"

private val issuedTokenKey = AttributeKey<IssuedToken>("IssuedToken")

private val openPaths = setOf(
    "/api/v1/scopes/all",
    "/api/v1/prefix/all",
    "/api/v1/apis",
    "/api/v1/delegationsources",
    "/api/v1/integrations",
)

fun requireMaskinportenToken(maskinporten: MaskinportenMock) = createRouteScopedPlugin("RequireMaskinportenToken") {
    onCall { call ->
        if (call.request.path() in openPaths) return@onCall

        val token = call.request.headers[HttpHeaders.Authorization]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring("Bearer ".length)
            ?.trim()
        val issued = token?.let(maskinporten::issuedToken)
            ?: throw UnauthorizedException("A valid Maskinporten token is required as Bearer")

        val accepted = if (call.request.httpMethod == HttpMethod.Get) setOf(READ, WRITE) else setOf(WRITE)
        if (issued.scope.split(' ').none { it in accepted }) {
            throw ForbiddenException("The token needs one of the scopes ${accepted.joinToString()}")
        }
        call.attributes.put(issuedTokenKey, issued)
    }
}

val ApplicationCall.organization: String?
    get() = attributes.getOrNull(issuedTokenKey)?.organizationNumber

fun requireOwner(owner: String, organization: String?) {
    if (organization != null && owner != organization) {
        throw ForbiddenException("The scope belongs to another organisation")
    }
}
