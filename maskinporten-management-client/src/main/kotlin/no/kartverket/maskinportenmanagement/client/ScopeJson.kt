package no.kartverket.maskinportenmanagement.client

import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

@JvmInline
public value class ScopeJson private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        private val FIELDS = setOf(
            "prefix", "subscope", "description", "long_description", "active", "allowed_integration_types", "owner_orgno",
            "token_type", "visibility", "requires_user_consent", "requires_user_authentication",
            "requires_pseudonymous_tokens", "delegation_source", "at_max_age", "authorization_max_lifetime",
            "accessible_for_all", "enforced_aud_for_access_token", "protected", "supports_european_businesses",
            "description#en", "description#nn", "description#se",
            "long_description#en", "long_description#nn", "long_description#se",
        )

        private val NAME_FIELDS = listOf("prefix", "subscope")

        // Unlike kotlinx, Jackson can refuse a name given twice, so the access check in front of us and Digdir cannot
        // read different scopes from the body
        private val strictJson = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

        public fun of(value: String): ScopeJson {
            val json = try {
                strictJson.readTree(value)
            } catch (e: JacksonException) {
                throw IllegalArgumentException("The body is not valid JSON: ${e.originalMessage}")
            }
            require(json is ObjectNode) { "Expected a scope as a JSON object" }
            val unknown = json.propertyNames().firstOrNull { it !in FIELDS }
            require(unknown == null) { "The field \"$unknown\" is not allowed" }
            val missing = NAME_FIELDS.firstOrNull { json[it]?.takeIf { it.isString }?.stringValue().isNullOrBlank() }
            require(missing == null) { "The field \"$missing\" is required, as text" }
            return ScopeJson(value)
        }
    }
}
