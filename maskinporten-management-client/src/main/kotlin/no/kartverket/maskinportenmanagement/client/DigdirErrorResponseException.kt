package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse

public class DigdirErrorResponseException internal constructor(response: OutgoingResponse) :
    MaskinportenManagementException("Digdir answered HTTP ${response.statusCode}") {

    public val statusCode: Int = response.statusCode

    public val responseBody: String = response.text()

    /** Digdir's `error_description`, else its `error`, or null when neither is a non-blank string. */
    public val explanation: String? = explanationIn(responseBody)
}

private fun explanationIn(answer: String): String? {
    val fields = runCatching { Json.parseToJsonElement(answer) }.getOrNull() as? JsonObject ?: return null
    return listOf("error_description", "error").firstNotNullOfOrNull { name ->
        (fields[name] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
    }
}
