package no.kartverket.maskinportenmanagement.restserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.restserver.filtering.ScopeFilter

internal val testApps = mapOf(
    "tilgangsstyring/demo" to ScopeFilter(exact = emptyList(), prefix = listOf("kartverk:tilgangsstyring/")),
    "matrikkel/matrikkel-api" to ScopeFilter(
        exact = listOf("kartverk:matrikkel.read", "kartverk:matrikkel.write", "kartverk:adresse.read"),
        prefix = emptyList(),
    ),
)

// Answers in OPA's format, as the filtering service does: the server sends its input, and gets the policy's answer
// back as result. Unknown apps get empty lists.
internal val fakeFilteringService = OutgoingHttpClient { request ->
    val input = request.body
        ?.let { runCatching { json.decodeFromString(FilteringRequest.serializer(), it.decodeToString()) }.getOrNull() }
        ?.input
    val clientCertificate = input?.headers?.entries?.firstOrNull { it.key.equals(CLIENT_CERTIFICATE_HEADER, ignoreCase = true) }?.value
    val app = appFrom(clientCertificate)
        ?: return@OutgoingHttpClient OutgoingResponse(
            400,
            "text/plain",
            "input.headers must have ${CLIENT_CERTIFICATE_HEADER.lowercase()} with a SPIFFE URI".encodeToByteArray(),
        )
    val filter = testApps[app] ?: ScopeFilter(exact = emptyList(), prefix = emptyList())
    OutgoingResponse(200, "application/json", json.encodeToString(FilteringResponse.serializer(), FilteringResponse(filter)).encodeToByteArray())
}

private val json = Json { ignoreUnknownKeys = true }

private val spiffeUri = Regex("""URI=spiffe://[^/;,"]+/ns/([^/;,"]+)/sa/([^/;,"]+)""")

// The last URI, like OPA, since Istio appends the caller it verified after anything the caller sent itself
private fun appFrom(clientCertificate: String?): String? =
    clientCertificate?.let { spiffeUri.findAll(it).lastOrNull() }?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }

@Serializable
private data class FilteringRequest(val input: FilteringInput)

@Serializable
private data class FilteringInput(val headers: Map<String, String> = emptyMap())

@Serializable
private data class FilteringResponse(val result: ScopeFilter)
