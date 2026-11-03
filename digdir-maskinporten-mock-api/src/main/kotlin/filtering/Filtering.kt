package com.maskinporten.filtering

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val CLIENT_CERTIFICATE_HEADER = "X-Forwarded-Client-Cert"

@Serializable
data class ScopeFilter(val exact: List<String>, val prefix: List<String>)

// The same shape as OPA's data API: the server sends its input, and gets the policy's answer back as result
@Serializable
data class FilteringRequest(val input: FilteringInput)

@Serializable
data class FilteringInput(val headers: Map<String, String> = emptyMap())

@Serializable
data class FilteringResponse(val result: ScopeFilter)

val testApps = mapOf(
    "tilgangsstyring/demo" to ScopeFilter(exact = emptyList(), prefix = listOf("kartverk:tilgangsstyring/")),
    "matrikkel/matrikkel-api" to ScopeFilter(
        exact = listOf("kartverk:matrikkel.read", "kartverk:matrikkel.write", "kartverk:adresse.read"),
        prefix = emptyList(),
    ),
)

private val json = Json { ignoreUnknownKeys = true }

private val spiffeUri = Regex("""URI=spiffe://[^/;,"]+/ns/([^/;,"]+)/sa/([^/;,"]+)""")

// The last URI, like OPA, since Istio appends the caller it verified after anything the caller sent itself
fun appFrom(clientCertificate: String?): String? =
    clientCertificate?.let { spiffeUri.findAll(it).lastOrNull() }?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }

fun Route.filteringRoutes() {
    post("/filtering") {
        val request = runCatching { json.decodeFromString(FilteringRequest.serializer(), call.receiveText()) }.getOrNull()
        val clientCertificate = request?.input?.headers?.entries
            ?.firstOrNull { it.key.equals(CLIENT_CERTIFICATE_HEADER, ignoreCase = true) }?.value
        val app = appFrom(clientCertificate)
            ?: return@post call.respondText(
                "input.headers must have ${CLIENT_CERTIFICATE_HEADER.lowercase()} with a SPIFFE URI",
                status = HttpStatusCode.BadRequest,
            )
        call.respond(FilteringResponse(testApps[app] ?: ScopeFilter(exact = emptyList(), prefix = emptyList())))
    }
}
