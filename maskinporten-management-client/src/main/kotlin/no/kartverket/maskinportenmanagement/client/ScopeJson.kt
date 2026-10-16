package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// A scope in Digdir's format, sent on as it is. Each field must be one of Digdir's, given once, so the access check
// in front of us and Digdir cannot read different values from it
@JvmInline
public value class ScopeJson private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        // Digdir's ExternalScopeRequest, without the read-only descriptions
        private val FIELDS = setOf(
            "prefix", "subscope", "description", "long_description", "active", "allowed_integration_types", "owner_orgno",
            "token_type", "visibility", "requires_user_consent", "requires_user_authentication",
            "requires_pseudonymous_tokens", "delegation_source", "at_max_age", "authorization_max_lifetime",
            "accessible_for_all", "enforced_aud_for_access_token", "protected", "supports_european_businesses",
        )
        private val TRANSLATION = Regex("(long_)?description#[a-z]{2,3}")

        public fun of(value: String): ScopeJson {
            val json = try {
                Json.parseToJsonElement(value)
            } catch (e: SerializationException) {
                null
            }
            require(json is JsonObject) { "Expected a scope as a JSON object" }
            val fields = topLevelFields(value)
            val repeated = fields.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
            require(repeated == null) { "The field \"$repeated\" is given more than once" }
            val unknown = fields.firstOrNull { it !in FIELDS && !TRANSLATION.matches(it) }
            require(unknown == null) { "Digdir has no field \"$unknown\"" }
            return ScopeJson(value)
        }

        // The parser keeps only the last of two equal names, so the names are read from the text itself
        private fun topLevelFields(json: String): List<String> {
            val fields = mutableListOf<String>()
            var depth = 0
            var expectingName = false
            var i = 0
            while (i < json.length) {
                when (json[i]) {
                    '"' -> {
                        val end = endOfString(json, i)
                        if (depth == 1 && expectingName) {
                            fields += Json.decodeFromString(String.serializer(), json.substring(i, end + 1))
                            expectingName = false
                        }
                        i = end
                    }

                    '{' -> if (++depth == 1) expectingName = true

                    '[' -> depth++

                    '}', ']' -> depth--

                    ',' -> if (depth == 1) expectingName = true
                }
                i++
            }
            return fields
        }

        private fun endOfString(json: String, start: Int): Int {
            var i = start + 1
            while (json[i] != '"') i += if (json[i] == '\\') 2 else 1
            return i
        }
    }
}
